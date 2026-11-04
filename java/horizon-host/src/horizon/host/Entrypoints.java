package horizon.host;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;

/**
 * Runs a mod's Fabric entrypoint.
 *
 * <p>This is the first place where a mod's own code executes. The engine sends
 * an {@link Wire.Entrypoint} frame naming a jar and a class; this class loads the
 * class from that jar, instantiates it, and calls the interface method Fabric
 * Loader would have called.
 *
 * <p>Loaders are cached per jar path, so a mod's static state survives between
 * entrypoint calls (main, then client), exactly as it would under Loader.
 *
 * <p>Result string: {@code ok:<entrypoint>:<class>[:<evidence>]} or
 * {@code error:<throwable class>:<message>}. The optional evidence comes from a
 * static {@code evidence()} method when the class defines one; it exists so a
 * test can prove the body ran, and is ignored for ordinary mods.
 */
public final class Entrypoints {
    private static final Map<String, URLClassLoader> LOADERS = new HashMap<>();

    private Entrypoints() {
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

    /**
     * Instantiates {@code className} from {@code jarPath} and calls its entrypoint.
     *
     * @param entrypoint {@code main}, {@code client} or {@code server}
     */
    public static String invoke(String jarPath, String className, String entrypoint, String modId) {
        if (className == null || className.isEmpty()) {
            return "error:java.lang.IllegalArgumentException:empty class name";
        }

        String kind = entrypoint == null || entrypoint.isEmpty() ? "main" : entrypoint;

        try {
            ClassLoader loader = loaderFor(jarPath);
            Class<?> clazz = Class.forName(className, true, loader);
            Object instance = clazz.getDeclaredConstructor().newInstance();

            switch (kind) {
                case "main" -> ((ModInitializer) instance).onInitialize();
                case "client" -> ((ClientModInitializer) instance).onInitializeClient();
                case "server" -> ((DedicatedServerModInitializer) instance).onInitializeServer();
                default -> {
                    return "error:java.lang.IllegalArgumentException:unknown entrypoint '" + kind
                            + "' (expected main, client or server)";
                }
            }

            String id = modId == null ? "" : modId;
            return "ok:" + kind + ":" + clazz.getName() + (id.isEmpty() ? "" : ":" + id)
                    + evidence(clazz);
        } catch (Throwable t) {
            String message = t.getMessage();
            return "error:" + t.getClass().getName() + ":"
                    + (message == null ? "" : message);
        }
    }

    /** Calls a static {@code evidence()} on the mod class when it has one. */
    private static String evidence(Class<?> clazz) {
        try {
            Method method = clazz.getMethod("evidence");
            Object value = method.invoke(null);
            return ":" + value;
        } catch (NoSuchMethodException absent) {
            return "";
        } catch (Throwable broken) {
            return ":evidence-error=" + broken.getClass().getName();
        }
    }
}
