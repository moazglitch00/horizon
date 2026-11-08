package horizon.host;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.launch.MappingConfiguration;

/**
 * The launcher Fabric Loader sees during a discovery probe.
 *
 * <p>Loader's discovery path asks the launcher four things: the environment, whether this is a
 * development environment, the target class loader and — through
 * {@code ClasspathModCandidateFinder} — nothing else in production mode. Everything here answers
 * those questions truthfully for a child process that is not launching a game.
 *
 * <p>The methods that belong to launching (mixing into a real class path, mutating the class path,
 * registering mixin parent prefixes) throw with the roadmap phase that owns them instead of
 * pretending to work. A mod cannot reach them in a discovery probe: discovery never calls
 * {@code FabricLoaderImpl.freeze()}, which is what would add mods to the class path.
 */
public final class HeadlessLauncher extends FabricLauncherBase {
    private final EnvType environment;
    private final Path gameDir;
    private final List<Path> classPath;
    /**
     * The loader Mixin resolves resources and mixin classes through
     * ({@code MixinServiceKnot.getResourceAsStream}/{@code findClass} delegate to the
     * launcher's target). It must see the mod jars: the JDK's application class loader
     * cannot be extended at runtime (Java 9+ is not a {@link URLClassLoader}), so the
     * host keeps its own child loader over the launcher class path and the mods.
     */
    private final ModClassLoader target;
    private final Set<String> loadedClasses = ConcurrentHashMap.newKeySet();
    private MappingConfiguration mappings;

    /** A URL class loader with a public {@code addURL}, so jars can join it after startup. */
    private static final class ModClassLoader extends URLClassLoader {
        ModClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        void add(URL url) {
            super.addURL(url);
        }
    }

    public HeadlessLauncher(EnvType environment, Path gameDir, List<Path> classPath) {
        this.environment = environment;
        this.gameDir = gameDir;
        this.classPath = new ArrayList<>(classPath);
        this.target = new ModClassLoader(urls(this.classPath),
                HeadlessLauncher.class.getClassLoader());
    }

    private static URL[] urls(List<Path> paths) {
        return paths.stream().map(path -> {
            try {
                return path.toUri().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("invalid class path entry " + path, e);
            }
        }).toArray(URL[]::new);
    }

    /**
     * Adds a mod jar to the target loader, so Mixin can read its configs and mixin classes
     * and so the host can define the mod's classes in the same loader Mixin sees.
     */
    public void addTargetJar(Path jar) {
        if (classPath.contains(jar)) {
            return;
        }

        try {
            target.add(jar.toUri().toURL());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("invalid mod jar " + jar, e);
        }

        classPath.add(jar);
    }

    public Path gameDir() {
        return gameDir;
    }

    public List<Path> classPath() {
        return classPath;
    }

    @Override
    public MappingConfiguration getMappingConfiguration() {
        // Constructed lazily: MappingConfiguration reads the game provider when mappings are
        // requested, and the provider is installed just after this launcher.
        if (mappings == null) {
            mappings = new MappingConfiguration();
        }

        return mappings;
    }

    @Override
    public void addToClassPath(Path path, String... allowedPrefixes) {
        throw launchOnly("addToClassPath", "the discovery class path is fixed");
    }

    @Override
    public void setAllowedPrefixes(Path path, String... prefixes) {
        throw launchOnly("setAllowedPrefixes", "mixin prefix registration is not part of discovery");
    }

    @Override
    public void setValidParentClassPath(Collection<Path> paths) {
        throw launchOnly("setValidParentClassPath", "parent class path filtering is not part of discovery");
    }

    @Override
    public EnvType getEnvironmentType() {
        return environment;
    }

    @Override
    public boolean isClassLoaded(String name) {
        // The headless host has no real class loader behind the launcher's target:
        // classes are loaded on demand by the engine's LOAD_CLASS frames. Track the
        // ones loaded through the host so Mixin's "target loaded too early" audit
        // sees the truth instead of failing.
        return loadedClasses.contains(name);
    }

    /** Records a class as loaded, mirroring what a launching class loader would know. */
    void markClassLoaded(String name) {
        if (name != null) {
            loadedClasses.add(name);
        }
    }

    /**
     * Records a class the host defined by hand as loaded, on whichever
     * {@link HeadlessLauncher} Fabric Loader currently has installed.
     *
     * <p>The host defines transformed classes with a reflective
     * {@code ClassLoader.defineClass}, which the launcher cannot see. Telling it
     * keeps {@link #isClassLoaded(String)} truthful for Mixin's audit.
     */
    static void markLoadedOnCurrentLauncher(String name) {
        try {
            Object launcher = FabricLauncherBase.getLauncher();
            if (launcher instanceof HeadlessLauncher headless) {
                headless.markClassLoaded(name);
            }
        } catch (Throwable ignored) {
            // No launcher installed yet: nothing to record.
        }
    }

    /**
     * The target loader of the launcher Fabric Loader currently has installed, or
     * {@code null} when there is none. Mod classes must be defined in this loader: the
     * mixin classes Mixin loads live here too, so a mixin's handler and its target class
     * must not end up in two loaders (that would make a {@code checkcast} to the target
     * type fail with {@link LinkageError}).
     */
    static ClassLoader currentTarget() {
        try {
            Object launcher = FabricLauncherBase.getLauncher();
            if (launcher instanceof HeadlessLauncher headless) {
                return headless.getTargetClassLoader();
            }
        } catch (Throwable ignored) {
            // No launcher installed yet.
        }

        return null;
    }

    /** Adds a mod jar to the current launcher's target loader, if there is one. */
    static void addTargetJarToCurrentLauncher(Path jar) {
        try {
            Object launcher = FabricLauncherBase.getLauncher();
            if (launcher instanceof HeadlessLauncher headless) {
                headless.addTargetJar(jar);
            }
        } catch (Throwable ignored) {
            // No launcher installed yet: nothing to extend.
        }
    }

    @Override
    public Class<?> loadIntoTarget(String name) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(name, false, target);
        markClassLoaded(name);
        return clazz;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return target.getResourceAsStream(name);
    }

    @Override
    public ClassLoader getTargetClassLoader() {
        return target;
    }

    @Override
    public byte[] getClassByteArray(String name, boolean allowFromParent) throws IOException {
        try (InputStream in = target.getResourceAsStream(name.replace('.', '/') + ".class")) {
            return in == null ? null : in.readAllBytes();
        }
    }

    @Override
    public Manifest getManifest(Path origin) {
        try (JarFile jar = new JarFile(origin.toFile())) {
            return jar.getManifest();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read the manifest of " + origin, e);
        }
    }

    @Override
    public String getEntrypoint() {
        return "net.minecraft.client.main.Main";
    }

    @Override
    public List<Path> getClassPath() {
        return classPath;
    }

    private static UnsupportedOperationException launchOnly(String method, String why) {
        return new UnsupportedOperationException("HeadlessLauncher." + method + ": " + why
                + ". Launching is roadmap phase 6; this host only runs Loader discovery (phase 2)");
    }
}
