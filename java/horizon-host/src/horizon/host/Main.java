package horizon.host;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

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

    private static final Map<String, Class<?>> loadedClasses = new HashMap<>();
    private static final Map<String, java.net.URLClassLoader> loadedClassLoaders = new HashMap<>();

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

        // One unreadable or unapplicable mixin config must not disable every other
        // mod's mixins. Mixin reads this option once, when it constructs the default
        // environment, and then downgrades a required config's prepare/apply failure
        // from fatal (MixinPrepareError / MixinApplyError) to a warning that drops the
        // config. The option's property is built from its parents, so the environment
        // option's key is "mixin.env" and this one is "mixin.env.ignoreRequired".
        // The host logs the failing mod and config on stderr either way.
        if (System.getProperty("mixin.env.ignoreRequired") == null) {
            System.setProperty("mixin.env.ignoreRequired", "true");
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

            // Initialise FabricLauncherBase.properties so that MixinBootstrap can
            // read system properties. HeadlessLauncher does not set this field.
            Class<?> launcherBaseClass = Class.forName(
                    "net.fabricmc.loader.impl.launch.FabricLauncherBase");
            Field launcherField = launcherBaseClass.getDeclaredField("launcher");
            launcherField.setAccessible(true);
            Object launcher = launcherField.get(null);
            if (launcher == null) {
                // Mixin derives its side from the launcher (MixinServiceKnot.getSideName
                // -> FabricLauncherBase.getLauncher().getEnvironmentType()), so a host
                // started with --side server must not report CLIENT or server-only
                // mixin configs would be applied on a client and vice versa.
                net.fabricmc.api.EnvType envType = side == Wire.SIDE_SERVER
                        ? net.fabricmc.api.EnvType.SERVER
                        : net.fabricmc.api.EnvType.CLIENT;
                launcher = new HeadlessLauncher(
                        envType,
                        Paths.get("."),
                        new ArrayList<>());
                launcherBaseClass.getMethod("setLauncher",
                        Class.forName("net.fabricmc.loader.impl.launch.FabricLauncher"))
                        .invoke(null, launcher);
                launcher = launcherField.get(null);
            }
            Field propsField = launcherBaseClass.getDeclaredField("properties");
            propsField.setAccessible(true);
            propsField.set(launcher, new java.util.Properties());


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
                    Entrypoints.initGameInfo(
                            Paths.get(discover.modsDir()), discover.gameVersion(), discover.side());
                    discover(wire, discover);
                } else if (frame instanceof Wire.Shutdown shutdown) {
                    System.exit(shutdown.code());
                } else if (frame instanceof Wire.RegistrySnapshot snapshot) {
                    System.err.println("horizon host: received a registry snapshot of "
                            + snapshot.json().length() + " bytes");
                } else if (frame instanceof Wire.Event event) {
                    System.err.println("horizon host: received event '" + event.name() + "'");
                } else if (frame instanceof Wire.LoadClass loadClass) {
                    try {
                        System.err.println("horizon host: loading class " + loadClass.className());

                        byte[] classBytes;
                        ClassLoader loader;
                        if (loadClass.jarPath().isEmpty()) {
                            // System class - just load it normally
                            Class<?> clazz = Class.forName(loadClass.className());
                            loadedClasses.put(loadClass.className(), clazz);
                            HeadlessLauncher.markLoadedOnCurrentLauncher(loadClass.className());
                            System.err.println("horizon host: loaded " + clazz.getName());
                            wire.write(new Wire.ClassLoaded(clazz.getName()));
                            continue;
                        } else {
                            // Mod classes belong in the launcher's target loader: that is the
                            // loader Mixin resolves the mod's mixin classes through, so a mixin
                            // handler and its target must be defined by the same loader. A
                            // per-jar loader would make a checkcast to the target type fail.
                            loader = HeadlessLauncher.currentTarget();
                            if (loader == null) {
                                java.net.URL[] urls = { Paths.get(loadClass.jarPath()).toUri().toURL() };
                                String jarKey = loadClass.jarPath();
                                loader = loadedClassLoaders.computeIfAbsent(jarKey,
                                        key -> new java.net.URLClassLoader(urls,
                                                ClassLoader.getSystemClassLoader()));
                            }

                            // Read raw class bytes from the jar so we can run Mixin transformations
                            String classPath = loadClass.className().replace('.', '/') + ".class";
                            try (InputStream is = loader.getResourceAsStream(classPath)) {
                                if (is == null) {
                                    throw new java.io.FileNotFoundException(
                                            "Class not found in jar: " + classPath);
                                }
                                classBytes = is.readAllBytes();
                            }

                            // If the class is already defined, the JVM cannot transform it any
                            // more. That is the "target loaded too early" condition; report it
                            // instead of poisoning the loader with a duplicate definition.
                            Class<?> existing = loadedClass(loader, loadClass.className());
                            if (existing != null) {
                                System.err.println("horizon host: " + loadClass.className()
                                        + " was already loaded; Mixin cannot transform it");
                                loadedClasses.put(loadClass.className(), existing);
                                HeadlessLauncher.markLoadedOnCurrentLauncher(loadClass.className());
                                wire.write(new Wire.ClassLoaded(existing.getName()));
                                continue;
                            }
                        }

                        // Apply Mixin bytecode transformations if Mixin is initialized
                        classBytes = applyMixinTransformations(loadClass.className(), classBytes);

                        // Define the class with (possibly transformed) bytes
                        Class<?> clazz = defineClassViaReflection(
                                loader, loadClass.className(), classBytes);
                        loadedClasses.put(loadClass.className(), clazz);
                        HeadlessLauncher.markLoadedOnCurrentLauncher(loadClass.className());
                        System.err.println("horizon host: loaded " + clazz.getName());
                        wire.write(new Wire.ClassLoaded(clazz.getName()));
                    } catch (Throwable t) {
                        t.printStackTrace();
                        wire.write(new Wire.Fatal(t.getClass().getName() + ": " + t.getMessage()));
                    }
                } else if (frame instanceof Wire.InvokeMethod invokeMethod) {
                    try {
                        Class<?> clazz = loadedClasses.get(invokeMethod.className());
                        if (clazz == null) {
                            clazz = Class.forName(invokeMethod.className());
                            loadedClasses.put(invokeMethod.className(), clazz);
                        }
                        java.lang.reflect.Method method = clazz.getMethod(invokeMethod.methodName());
                        Object instance = clazz.getDeclaredConstructor().newInstance();
                        Object result = method.invoke(instance);
                        wire.write(new Wire.InvokeResult(
                                result == null ? "null" : result.toString()));
                    } catch (Throwable t) {
                        Throwable cause = t;
                        while (cause.getCause() != null && cause.getCause() != cause) {
                            cause = cause.getCause();
                        }
                        System.err.println("horizon host: invoke method failed for "
                                + invokeMethod.className() + "." + invokeMethod.methodName());
                        t.printStackTrace();
                        wire.write(new Wire.Fatal(t.getClass().getName() + ": "
                                + (cause.getMessage() != null ? cause.getMessage() : t.getMessage())));
                    }
                } else if (frame instanceof Wire.Entrypoint entrypoint) {
                    String result = Entrypoints.invoke(
                            entrypoint.jarPath(),
                            entrypoint.className(),
                            entrypoint.entrypoint(),
                            entrypoint.modId());
                    System.err.println("horizon host: entrypoint " + entrypoint.entrypoint()
                            + " " + entrypoint.className() + " -> " + result);
                    wire.write(new Wire.InvokeResult(result));
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

    /**
     * Apply Mixin bytecode transformations to a class if Mixin is initialized.
     *
     * <p>Reads Mixin's internal {@link IMixinTransformer} via reflection and runs
     * the class bytes through it. Returns the original bytes unchanged if Mixin
     * is not yet initialised or the transformer cannot be found.
     */
    private static byte[] applyMixinTransformations(String className, byte[] bytes) {
        try {
            Class<?> envClass = Class.forName(
                    "org.spongepowered.asm.mixin.MixinEnvironment");
            Object env = envClass.getMethod("getDefaultEnvironment").invoke(null);
            if (env == null) {
                return bytes;
            }

            // MixinEnvironment has a private static IMixinTransformer field.
            Field transformerField = envClass.getDeclaredField("transformer");
            transformerField.setAccessible(true);
            Object transformer = transformerField.get(null);
            if (transformer == null) {
                return bytes;
            }

            IMixinTransformer mixinTransformer =
                    (IMixinTransformer) transformer;
            byte[] transformed = mixinTransformer.transformClassBytes(
                    className, className, bytes);
            if (transformed != bytes && transformed.length != bytes.length) {
                System.err.println("horizon host: Mixin transformed "
                        + className + " (" + bytes.length + " -> "
                        + transformed.length + " bytes)");
            }
            return transformed;
        } catch (Throwable t) {
            // Mixin not yet initialised or transformation failed - use raw bytes
            System.err.println("horizon host: Mixin transform skipped for "
                    + className + ": " + t.getMessage());
            return bytes;
        }
    }

    private static Class<?> defineClassViaReflection(ClassLoader loader,
            String className, byte[] bytes) throws Exception {
        Method defineClass = ClassLoader.class.getDeclaredMethod(
                "defineClass", String.class, byte[].class, int.class, int.class);
        defineClass.setAccessible(true);
        return (Class<?>) defineClass.invoke(loader, className, bytes, 0, bytes.length);
    }

    /** The class a loader already defined, or {@code null} when it has not. */
    private static Class<?> loadedClass(ClassLoader loader, String className) {
        try {
            Method findLoadedClass = ClassLoader.class.getDeclaredMethod(
                    "findLoadedClass", String.class);
            findLoadedClass.setAccessible(true);
            return (Class<?>) findLoadedClass.invoke(loader, className);
        } catch (Throwable t) {
            return null;
        }
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
