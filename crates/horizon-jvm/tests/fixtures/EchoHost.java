import horizon.host.Wire;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * A host that speaks the pipe but never launches Minecraft. The Rust host tests use it to prove
 * handshake, ping, discovery, shutdown, crash and timeout behaviour without a JDK 25 mod set.
 */
public final class EchoHost {
    public static void main(String[] args) throws Exception {
        PrintStream frames = new PrintStream(
                new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        System.setOut(System.err);

        Wire wire = new Wire(System.in, frames);
        wire.write(new Wire.Hello(Wire.PROTOCOL_VERSION, "echo-host/1", "echo-loader",
                "26.3", Wire.SIDE_CLIENT));

        while (true) {
            Wire.Frame frame = wire.read();
            if (frame == null) {
                return;
            }

            if (frame instanceof Wire.HelloAck) {
                // nothing to do; the handshake is complete
            } else if (frame instanceof Wire.Ping ping) {
                wire.write(new Wire.Pong(ping.nonce()));
            } else if (frame instanceof Wire.Discover discover) {
                wire.write(new Wire.DiscoverResult(
                        "{\"ok\":true,\"loader\":\"echo\",\"gameVersion\":\"" + discover.gameVersion()
                                + "\",\"side\":\"client\",\"javaVersion\":25,\"roots\":0,\"mods\":[],"
                                + "\"order\":[],\"disabled\":[],\"errors\":[],\"warnings\":[],"
                                + "\"elapsedMillis\":0}"));
            } else if (frame instanceof Wire.LoadClass loadClass) {
                try {
                    ClassLoader loader = EchoHost.class.getClassLoader();
                    Class<?> clazz = Class.forName(loadClass.className(), true, loader);
                    wire.write(new Wire.ClassLoaded(clazz.getName()));
                } catch (Throwable t) {
                    t.printStackTrace();
                    wire.write(new Wire.Fatal(t.getClass().getName() + ": " + t.getMessage()));
                }
            } else if (frame instanceof Wire.InvokeMethod invokeMethod) {
                try {
                    Class<?> clazz = Class.forName(invokeMethod.className());
                    java.lang.reflect.Method method = clazz.getMethod(invokeMethod.methodName());
                    Object result = method.invoke(null);
                    wire.write(new Wire.InvokeResult(
                            result == null ? "null" : result.toString()));
                } catch (Throwable t) {
                    t.printStackTrace();
                    wire.write(new Wire.Fatal(t.getClass().getName() + ": " + t.getMessage()));
                }
            } else if (frame instanceof Wire.Shutdown shutdown) {
                System.exit(shutdown.code());
            } else {
                wire.write(new Wire.Log(2, "echo ignored " + Wire.describe(frame)));
            }
        }
    }
}
