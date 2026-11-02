package horizon.host;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The framed pipe between the Rust engine and this host.
 *
 * <p>Every frame is a 4-byte little-endian payload length, a one-byte tag and the tag's fields.
 * This file and {@code crates/horizon-jvm/src/protocol.rs} are the two halves of one contract: a
 * change to a tag, a field or a limit must change both, and the protocol version must go up.
 *
 * <p>Strings are a 4-byte length and UTF-8 bytes. There are no Java object serialisation tricks:
 * the engine parses every byte, so the format stays readable by hand in a hex dump.
 */
public final class Wire implements Closeable {
    public static final int PROTOCOL_VERSION = 1;

    /** The largest frame either side will read or write (payload, without the length prefix). */
    public static final int MAX_FRAME = 8 << 20;
    /** Handshake, ping, pong and shutdown messages are control traffic, not payloads. */
    public static final int MAX_CONTROL_FRAME = 4 << 10;

    public static final int TAG_HELLO = 1;
    public static final int TAG_HELLO_ACK = 2;
    public static final int TAG_PING = 3;
    public static final int TAG_PONG = 4;
    public static final int TAG_DISCOVER = 5;
    public static final int TAG_DISCOVER_RESULT = 6;
    public static final int TAG_LOG = 7;
    public static final int TAG_FATAL = 8;
    public static final int TAG_SHUTDOWN = 9;
    public static final int TAG_REGISTRY_SNAPSHOT = 10;
    public static final int TAG_EVENT = 11;
    public static final int TAG_LOAD_CLASS = 12;
    public static final int TAG_CLASS_LOADED = 13;
    public static final int TAG_INVOKE_METHOD = 14;
    public static final int TAG_INVOKE_RESULT = 15;

    /** Client or server, as the engine passes {@code --side} and Fabric's {@code EnvType}. */
    public static final int SIDE_CLIENT = 0;
    public static final int SIDE_SERVER = 1;

    /** A message on the pipe. */
    public sealed interface Frame permits Hello, HelloAck, Ping, Pong, Discover, DiscoverResult,
            Log, Fatal, Shutdown, RegistrySnapshot, Event, LoadClass, ClassLoaded,
            InvokeMethod, InvokeResult {
    }

    public record Hello(int protocol, String host, String loader, String gameVersion, int side)
            implements Frame {
    }

    public record HelloAck(int protocol) implements Frame {
    }

    public record Ping(long nonce) implements Frame {
    }

    public record Pong(long nonce) implements Frame {
    }

    public record Discover(String modsDir, String gameDir, String gameVersion, int side)
            implements Frame {
    }

    public record DiscoverResult(String json) implements Frame {
    }

    public record Log(int level, String message) implements Frame {
    }

    public record Fatal(String message) implements Frame {
    }

    public record Shutdown(int code) implements Frame {
    }

    public record RegistrySnapshot(String json) implements Frame {
    }

    public record Event(String name, String payload) implements Frame {
    }

    public record LoadClass(String jarPath, String className) implements Frame {
    }

    public record ClassLoaded(String className) implements Frame {
    }

    public record InvokeMethod(String className, String methodName, String argsJson) implements Frame {
    }

    public record InvokeResult(String resultJson) implements Frame {
    }

    public static boolean isControl(Frame frame) {
        return frame instanceof Hello || frame instanceof HelloAck || frame instanceof Ping
                || frame instanceof Pong || frame instanceof Shutdown;
    }

    private final DataInputStream in;
    private final OutputStream out;

    public Wire(InputStream in, OutputStream out) {
        this.in = new DataInputStream(in);
        this.out = out;
    }

    /** Reads one frame, or returns {@code null} when the engine closed the pipe cleanly. */
    public Frame read() throws IOException {
        int first = in.read();

        if (first < 0) {
            return null;
        }

        byte[] header = new byte[4];
        header[0] = (byte) first;

        try {
            in.readFully(header, 1, 3);
        } catch (EOFException e) {
            throw new IOException("truncated frame length", e);
        }

        int length = leInt(header);
        if (length < 1 || length > MAX_FRAME) {
            throw new IOException("frame length " + length + " is outside 1.." + MAX_FRAME);
        }

        byte[] payload = new byte[length];
        in.readFully(payload);

        return decode(payload);
    }

    public synchronized void write(Frame frame) throws IOException {
        byte[] payload = encode(frame);

        if (isControl(frame) && payload.length > MAX_CONTROL_FRAME) {
            throw new IOException("control frame " + describe(frame) + " is " + payload.length
                    + " bytes, over the " + MAX_CONTROL_FRAME + " byte limit");
        }

        byte[] header = new byte[4];
        header[0] = (byte) payload.length;
        header[1] = (byte) (payload.length >>> 8);
        header[2] = (byte) (payload.length >>> 16);
        header[3] = (byte) (payload.length >>> 24);
        out.write(header);
        out.write(payload);
        out.flush();
    }

    public static String describe(Frame frame) {
        if (frame == null) {
            return "nothing";
        }

        return switch (frame) {
            case Hello hello -> "hello(protocol " + hello.protocol() + ")";
            case HelloAck ack -> "hello-ack(protocol " + ack.protocol() + ")";
            case Ping ping -> "ping(" + ping.nonce() + ")";
            case Pong pong -> "pong(" + pong.nonce() + ")";
            case Discover discover -> "discover(" + discover.modsDir() + ")";
            case DiscoverResult result -> "discover-result(" + result.json().length() + " chars)";
            case Log log -> "log(" + log.level() + ")";
            case Fatal fatal -> "fatal";
            case Shutdown shutdown -> "shutdown(" + shutdown.code() + ")";
            case RegistrySnapshot snapshot -> "registry-snapshot(" + snapshot.json().length() + " chars)";
            case Event event -> "event(" + event.name() + ")";
            case LoadClass loadClass -> "load-class(" + loadClass.className() + ")";
            case ClassLoaded classLoaded -> "class-loaded(" + classLoaded.className() + ")";
            case InvokeMethod invokeMethod -> "invoke-method(" + invokeMethod.methodName() + ")";
            case InvokeResult invokeResult -> "invoke-result(" + invokeResult.resultJson().length() + " chars)";
        };
    }

    public static byte[] encode(Frame frame) throws IOException {
        Writer out = new Writer();

        switch (frame) {
            case Hello hello -> {
                out.u8(TAG_HELLO);
                out.u32(hello.protocol());
                out.string(hello.host());
                out.string(hello.loader());
                out.string(hello.gameVersion());
                out.u8(hello.side());
            }
            case HelloAck ack -> {
                out.u8(TAG_HELLO_ACK);
                out.u32(ack.protocol());
            }
            case Ping ping -> {
                out.u8(TAG_PING);
                out.u64(ping.nonce());
            }
            case Pong pong -> {
                out.u8(TAG_PONG);
                out.u64(pong.nonce());
            }
            case Discover discover -> {
                out.u8(TAG_DISCOVER);
                out.string(discover.modsDir());
                out.string(discover.gameDir());
                out.string(discover.gameVersion());
                out.u8(discover.side());
            }
            case DiscoverResult result -> {
                out.u8(TAG_DISCOVER_RESULT);
                out.string(result.json());
            }
            case Log log -> {
                out.u8(TAG_LOG);
                out.u8(log.level());
                out.string(log.message());
            }
            case Fatal fatal -> {
                out.u8(TAG_FATAL);
                out.string(fatal.message());
            }
            case Shutdown shutdown -> {
                out.u8(TAG_SHUTDOWN);
                out.u32(shutdown.code());
            }
            case RegistrySnapshot snapshot -> {
                out.u8(TAG_REGISTRY_SNAPSHOT);
                out.string(snapshot.json());
            }
            case Event event -> {
                out.u8(TAG_EVENT);
                out.string(event.name());
                out.string(event.payload());
            }
            case LoadClass loadClass -> {
                out.u8(TAG_LOAD_CLASS);
                out.string(loadClass.jarPath());
                out.string(loadClass.className());
            }
            case ClassLoaded classLoaded -> {
                out.u8(TAG_CLASS_LOADED);
                out.string(classLoaded.className());
            }
            case InvokeMethod invokeMethod -> {
                out.u8(TAG_INVOKE_METHOD);
                out.string(invokeMethod.className());
                out.string(invokeMethod.methodName());
                out.string(invokeMethod.argsJson());
            }
            case InvokeResult invokeResult -> {
                out.u8(TAG_INVOKE_RESULT);
                out.string(invokeResult.resultJson());
            }
        }

        return out.toByteArray();
    }

    public static Frame decode(byte[] payload) throws IOException {
        if (payload.length < 1) {
            throw new IOException("empty frame");
        }

        Reader in = new Reader(payload);
        int tag = in.u8();

        Frame frame = switch (tag) {
            case TAG_HELLO -> new Hello(in.u32(), in.string(), in.string(), in.string(), in.u8());
            case TAG_HELLO_ACK -> new HelloAck(in.u32());
            case TAG_PING -> new Ping(in.u64());
            case TAG_PONG -> new Pong(in.u64());
            case TAG_DISCOVER -> new Discover(in.string(), in.string(), in.string(), in.u8());
            case TAG_DISCOVER_RESULT -> new DiscoverResult(in.string());
            case TAG_LOG -> new Log(in.u8(), in.string());
            case TAG_FATAL -> new Fatal(in.string());
            case TAG_SHUTDOWN -> new Shutdown(in.u32());
            case TAG_REGISTRY_SNAPSHOT -> new RegistrySnapshot(in.string());
            case TAG_EVENT -> new Event(in.string(), in.string());
            case TAG_LOAD_CLASS -> new LoadClass(in.string(), in.string());
            case TAG_CLASS_LOADED -> new ClassLoaded(in.string());
            case TAG_INVOKE_METHOD -> new InvokeMethod(in.string(), in.string(), in.string());
            case TAG_INVOKE_RESULT -> new InvokeResult(in.string());
            default -> throw new IOException("unknown frame tag " + tag);
        };

        in.expectEnd();
        return frame;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private static int leInt(byte[] bytes) {
        return (bytes[0] & 0xff) | (bytes[1] & 0xff) << 8 | (bytes[2] & 0xff) << 16
                | (bytes[3] & 0xff) << 24;
    }

    private static final class Writer extends ByteArrayOutputStream {
        void u8(int value) {
            write(value & 0xff);
        }

        void u32(int value) {
            u8(value);
            u8(value >>> 8);
            u8(value >>> 16);
            u8(value >>> 24);
        }

        void u64(long value) {
            u32((int) value);
            u32((int) (value >>> 32));
        }

        void string(String value) throws IOException {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_FRAME - 16) {
                throw new IOException("string of " + bytes.length + " bytes is over the frame limit");
            }
            u32(bytes.length);
            write(bytes, 0, bytes.length);
        }
    }

    private static final class Reader {
        private final byte[] bytes;
        private int at;

        Reader(byte[] bytes) {
            this.bytes = bytes;
        }

        int u8() throws IOException {
            if (at >= bytes.length) {
                throw new IOException("frame ended in the middle of a field");
            }
            return bytes[at++] & 0xff;
        }

        int u32() throws IOException {
            return u8() | u8() << 8 | u8() << 16 | u8() << 24;
        }

        long u64() throws IOException {
            return (u32() & 0xffffffffL) | ((long) u32() << 32);
        }

        String string() throws IOException {
            int length = u32();
            if (length < 0 || length > MAX_FRAME - at) {
                throw new IOException("string length " + length + " does not fit the frame");
            }
            String value = new String(bytes, at, length, StandardCharsets.UTF_8);
            at += length;
            return value;
        }

        void expectEnd() throws IOException {
            if (at != bytes.length) {
                throw new IOException((bytes.length - at) + " trailing byte(s) after frame " + at);
            }
        }
    }
}
