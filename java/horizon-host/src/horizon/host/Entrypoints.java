package horizon.host;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a mod's Fabric entrypoint through Fabric Loader.
 *
 * <p>This is the first place where a mod's own code executes. The engine sends
 * an {@link Wire.Entrypoint} frame naming a jar and a class; this class loads
 * the class from that jar, instantiates it, and calls the interface method Fabric
 * Loader would have called.
 *
 * <p>On the first call, Fabric Loader is initialised via reflection (the host
 * sources compile without Fabric Loader on the classpath). {@code
 * FabricLoaderImpl#load()} discovers and resolves mods, initialises their
 * classloaders. The mod's entrypoint class is then loaded from its jar and its
 * entrypoint method invoked directly via reflection.
 *
 * <p>Result string: {@code ok:<entrypoint>:<class>[:<modid>]} or
 * {@code error:<throwable class>:<message>}.
 */
public final class Entrypoints {
    private static final Map<String, URLClassLoader> LOADERS = new HashMap<>();

    /** Game directory, set from the HELLO/DISCOVER handshake. */
    private static Path gameDir = java.nio.file.Paths.get(".");
    /** Game version, set from the HELLO/DISCOVER handshake. */
    private static String gameVersion = "26.3";
    /** Side (0=client, 1=server), set from the HELLO/DISCOVER handshake. */
    private static int side = 0;

    /** Whether Fabric Loader has been initialised (load() called). */
    private static boolean loaderInitialized;

    /** Cached reflective references to FabricLoaderImpl INSTANCE and load(). */
    private static Class<?> loaderImplClass;
    private static Object loaderImplInstance;
    private static Method loaderLoadMethod;

    /** Cached reflective reference to setGameProvider. */
    private static Method loaderSetGameProvider;
    /** Cached reflective constructor for HorizonGameProvider. */
    private static Constructor<?> horizonGameProviderCtor;

    /** Cached reflective references for FabricLauncherBase.setLauncher. */
    private static Class<?> launcherBaseClass;
    private static Method launcherSetLauncher;
    private static Class<?> headlessLauncherClass;
    private static Constructor<?> headlessLauncherCtor;

    private Entrypoints() {
    }

    /** Call once from Main.java when the HELLO/DISCOVER handshake is known. */
    public static void initGameInfo(Path gameDir0, String gameVersion0, int side0) {
        gameDir = gameDir0;
        gameVersion = gameVersion0;
        side = side0;
    }

    /** Resolve FabricLoaderImpl reflectively (Fabric Loader is on the runtime classpath, not the compile classpath). */
    private static void ensureLoaderRefs() throws Exception {
        if (loaderImplClass != null) {
            return;
        }
        loaderImplClass = Class.forName("net.fabricmc.loader.impl.FabricLoaderImpl");
        loaderImplInstance = loaderImplClass.getField("INSTANCE").get(null);
        loaderLoadMethod = loaderImplClass.getMethod("load");
        Class<?> gameProviderClass = Class.forName("net.fabricmc.loader.impl.game.GameProvider");
        loaderSetGameProvider = loaderImplClass.getMethod("setGameProvider", gameProviderClass);
        horizonGameProviderCtor = Class.forName("horizon.host.HorizonGameProvider")
                .getConstructor(Path.class, String.class, List.class);

        // Also resolve FabricLauncherBase.setLauncher and HeadlessLauncher constructor.
        launcherBaseClass = Class.forName("net.fabricmc.loader.impl.launch.FabricLauncherBase");
        launcherSetLauncher = launcherBaseClass.getMethod("setLauncher",
                Class.forName("net.fabricmc.loader.impl.launch.FabricLauncher"));
        headlessLauncherClass = Class.forName("horizon.host.HeadlessLauncher");
        Class<?> envTypeClass = Class.forName("net.fabricmc.api.EnvType");
        headlessLauncherCtor = headlessLauncherClass.getConstructor(
                envTypeClass, Path.class, List.class);
    }

    /**
     * Initialise Fabric Loader if it has not been initialised yet.
     *
     * <p>Calls {@code FabricLoaderImpl#load()} reflectively, which discovers
     * mods in the mods folder, resolves dependencies, creates mod classloaders.
     */
    private static synchronized void ensureLoaderLoaded() throws Exception {
        if (loaderInitialized) {
            return;
        }

        ensureLoaderRefs();

        // Set the FabricLauncherBase launcher before loading (Loader requires it).
        Class<?> envTypeClass = Class.forName("net.fabricmc.api.EnvType");
        Object environment = side == 1
                ? envTypeClass.getField("SERVER").get(null)
                : envTypeClass.getField("CLIENT").get(null);
        Object launcher = headlessLauncherCtor.newInstance(
                environment, gameDir, List.<Path>of()
        );
        launcherSetLauncher.invoke(null, launcher);

        // Set the game provider before loading (Loader requires it).
        Object gameProvider = horizonGameProviderCtor.newInstance(
                gameDir, gameVersion, List.of()
        );
        loaderSetGameProvider.invoke(loaderImplInstance, gameProvider);

        // load() discovers, resolves, initialises mod classloaders.
        loaderLoadMethod.invoke(loaderImplInstance);

        loaderInitialized = true;
    }

    /**
     * Ensure Fabric Loader is initialised and invoke the named mod's entrypoint.
     *
     * @param entrypoint {@code main}, {@code client} or {@code server}
     */
    public static String invoke(String jarPath, String className, String entrypoint, String modId) {
        if (className == null || className.isEmpty()) {
            return "error:java.lang.IllegalArgumentException:empty class name";
        }

        String kind = entrypoint == null || entrypoint.isEmpty() ? "main" : entrypoint;

        try {
            // Initialise Fabric Loader on first call.
            ensureLoaderLoaded();

            // Load the entry point class from the mod jar using a URLClassLoader
            // with the host's classpath as parent, so Fabric API classes are
            // accessible.
            ClassLoader loader = loaderFor(jarPath);
            Class<?> clazz = Class.forName(className, true, loader);

            Object instance = clazz.getDeclaredConstructor().newInstance();

            // Invoke the entrypoint method reflectively so host sources compile
            // without Fabric Loader on the classpath.
            String methodName = switch (kind) {
                case "main" -> "onInitialize";
                case "client" -> "onInitializeClient";
                case "server" -> "onInitializeServer";
                default -> null;
            };
            if (methodName == null) {
                return "error:java.lang.IllegalArgumentException:unknown entrypoint '"
                        + kind + "' (expected main, client or server)";
            }

            // Verify the class implements the right Fabric entrypoint interface,
            // mirroring what Fabric Loader does. Interfaces are on the Fabric
            // Loader jar (runtime classpath), resolved reflectively.
            String interfaceName = switch (kind) {
                case "main" -> "net.fabricmc.api.ModInitializer";
                case "client" -> "net.fabricmc.api.ClientModInitializer";
                case "server" -> "net.fabricmc.api.DedicatedServerModInitializer";
                default -> null;
            };
            Class<?> initializerInterface = Class.forName(interfaceName);
            if (!initializerInterface.isAssignableFrom(clazz)) {
                throw new ClassCastException(
                        className + " does not implement " + interfaceName);
            }

            Method method = clazz.getMethod(methodName);
            method.invoke(instance);

            // Collect evidence from the mod's static evidence() method if present.
            String evidence = "";
            try {
                Method evidenceMethod = clazz.getMethod("evidence");
                Object value = evidenceMethod.invoke(null);
                evidence = ":" + value;
            } catch (NoSuchMethodException absent) {
                // No evidence method — that's fine.
            } catch (Throwable broken) {
                evidence = ":evidence-error=" + broken.getClass().getName();
            }

            String id = modId == null ? "" : modId;
            return "ok:" + kind + ":" + className + (id.isEmpty() ? "" : ":" + id)
                    + evidence + ":loader-initialised";
        } catch (Throwable t) {
            String message = t.getMessage();
            Throwable cause = t.getCause();
            if (cause != null && cause.getMessage() != null) {
                message = cause.getMessage();
            }
            return "error:" + t.getClass().getName() + ":"
                    + (message == null ? "" : message);
        }
    }

    /** The classloader for a mod jar; the host's own loader when {@code jarPath} is empty. */
    public static synchronized ClassLoader loaderFor(String jarPath) throws Exception {
        if (jarPath == null || jarPath.isEmpty()) {
            return Entrypoints.class.getClassLoader();
        }

        URLClassLoader cached = LOADERS.get(jarPath);
        if (cached != null) {
            return cached;
        }

        File jar = new File(jarPath);
        if (!jar.isFile()) {
            throw new java.io.FileNotFoundException("mod jar not found: " + jarPath);
        }

        URLClassLoader loader = new URLClassLoader(
                new URL[] {jar.toURI().toURL()},
                Entrypoints.class.getClassLoader());
        LOADERS.put(jarPath, loader);
        return loader;
    }
}
