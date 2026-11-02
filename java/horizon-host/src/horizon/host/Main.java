package horizon.host;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * The Horizon Java host: a child process the Rust engine starts when a player has Fabric mods.
 *
 * <p>It reads one framed {@link Wire} message at a time from stdin and writes frames to stdout.
 * Everything else the JVM prints — Fabric Loader's log, stack traces — goes to stderr, which the
 * engine appends to a log file. That split is why a mod's log line can never corrupt a frame.
 *
 * <p>This process never launches Minecraft. Phase 2 only asks Fabric Loader what it thinks of a
 * mods folder; launching is Phase 6 ({@code docs/fabric-compatibility/ROADMAP.md}).
 */
public final class Main {
    public static final String HOST_VERSION = "0.1.0";

    public static void main(String[] args) {
        PrintStream frames;

        try {
            frames = new PrintStream(
                    new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 64 * 1024),
                    false,
                    StandardCharsets.UTF_8);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(2);
            return;
        }

        // Fabric Loader logs through System.out. It must never be mistaken for a frame.
        System.setOut(System.err);

        Map<String, String> options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("horizon host: " + e.getMessage());
            System.exit(2);
            return;
        }

        int protocol = intOption(options, "protocol", Wire.PROTOCOL_VERSION);
        String gameVersion = options.getOrDefault("game-version", "unknown");
        int side = side(options.getOrDefault("side", "client"));

        Wire wire = new Wire(System.in, frames);

        if (protocol != Wire.PROTOCOL_VERSION) {
            try {
                wire.write(new Wire.Fatal("this host speaks protocol " + Wire.PROTOCOL_VERSION
                        + ", the engine asked for " + protocol));
            } catch (Throwable ignored) {
                // Nothing to do: the engine will report the exit code.
            }
            System.exit(3);
        }

        try {
            wire.write(new Wire.Hello(Wire.PROTOCOL_VERSION, "horizon-host/" + HOST_VERSION,
                    Discovery.loaderVersion(), gameVersion, side));
            System.err.println("horizon host " + HOST_VERSION + ": ready (loader "
                    + Discovery.loaderVersion() + ", Java " + Runtime.version().feature() + ")");

            while (true) {
                Wire.Frame frame = wire.read();

                if (frame == null) {
                    return;
                }

                if (frame instanceof Wire.HelloAck ack) {
                    if (ack.protocol() != Wire.PROTOCOL_VERSION) {
                        wire.write(new Wire.Fatal("protocol mismatch: engine speaks " + ack.protocol()
                                + ", host speaks " + Wire.PROTOCOL_VERSION));
                        System.exit(3);
                    }
                } else if (frame instanceof Wire.Ping ping) {
                    wire.write(new Wire.Pong(ping.nonce()));
                } else if (frame instanceof Wire.Discover discover) {
                    discover(wire, discover);
                } else if (frame instanceof Wire.Shutdown shutdown) {
                    System.exit(shutdown.code());
                } else if (frame instanceof Wire.RegistrySnapshot snapshot) {
                    System.err.println("horizon host: received a registry snapshot of "
                            + snapshot.json().length() + " bytes");
                } else if (frame instanceof Wire.Event event) {
                    System.err.println("horizon host: received event '" + event.name() + "'");
                } else {
                    wire.write(new Wire.Log(2, "host ignored " + Wire.describe(frame)));
                }
            }
        } catch (Throwable t) {
            try {
                wire.write(new Wire.Fatal(t.getClass().getName() + ": " + t.getMessage()));
            } catch (Throwable ignored) {
                // The pipe is gone; the engine will report the exit instead of a frame.
            }

            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void discover(Wire wire, Wire.Discover discover) throws Exception {
        long start = System.nanoTime();
        String json;

        try {
            Path mods = Paths.get(discover.modsDir());
            Path game = Paths.get(discover.gameDir());
            json = Discovery.run(mods, game, discover.gameVersion(), discover.side());
        } catch (Throwable t) {
            t.printStackTrace();
            json = Discovery.failure(discover.gameVersion(), discover.side(), t);
        }

        wire.write(new Wire.DiscoverResult(json));
        System.err.println("horizon host: discovery answered in "
                + (System.nanoTime() - start) / 1_000_000 + " ms");
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();

        for (int i = 0; i < args.length; i++) {
            String key = args[i];

            if (!key.startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument '" + key + "'");
            }

            if (i + 1 >= args.length) {
                throw new IllegalArgumentException(key + " needs a value");
            }

            options.put(key.substring(2), args[++i]);
        }

        return options;
    }

    private static int intOption(Map<String, String> options, String key, int fallback) {
        String value = options.get(key);
        return value == null ? fallback : Integer.parseInt(value);
    }

    private static int side(String value) {
        return switch (value) {
            case "client" -> Wire.SIDE_CLIENT;
            case "server" -> Wire.SIDE_SERVER;
            default -> throw new IllegalArgumentException("--side must be client or server");
        };
    }

    private Main() {
    }
}
