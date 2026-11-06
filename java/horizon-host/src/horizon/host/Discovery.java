package horizon.host;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.discovery.ClasspathModCandidateFinder;
import net.fabricmc.loader.impl.discovery.DirectoryModCandidateFinder;
import net.fabricmc.loader.impl.discovery.ModCandidateImpl;
import net.fabricmc.loader.impl.discovery.ModDiscoverer;
import net.fabricmc.loader.impl.discovery.ModResolutionException;
import net.fabricmc.loader.impl.discovery.ModResolver;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Runs Fabric Loader's own discovery and resolution over a mods folder and returns its verdict as
 * JSON.
 *
 * <p>This is a cross-check, not a second implementation: the Rust engine reads jars itself
 * (phase 1, {@code crates/horizon-fabric}); here Loader reads the same folder. When the two reports
 * disagree, one of them is wrong, and the difference is the bug.
 *
 * <p>The loader's log goes to stderr (see {@link Main}); only the report travels on the pipe.
 */
public final class Discovery {
    /** The report is capped well under {@link Wire#MAX_FRAME}. */
    private static final int MAX_MESSAGE = 16_000;

    public static String loaderVersion() {
        return FabricLoaderImpl.VERSION;
    }

    public static String run(Path modsDir, Path gameDir, String gameVersion, int side)
            throws Exception {
        long start = System.nanoTime();
        EnvType environment = side == Wire.SIDE_SERVER ? EnvType.SERVER : EnvType.CLIENT;
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (!Files.isDirectory(modsDir)) {
            warnings.add(modsDir + " does not exist: no mods are installed");
            return report(true, gameVersion, side, 0, List.of(), List.of(), List.of(), errors,
                    warnings, start);
        }

        FabricLoaderImpl loader = FabricLoaderImpl.INSTANCE;
        loader.setGameProvider(new HorizonGameProvider(gameDir, gameVersion, List.of()));

        VersionOverrides versionOverrides = new VersionOverrides();
        DependencyOverrides dependencyOverrides = new DependencyOverrides(gameDir.resolve("config"));

        ModDiscoverer discoverer = new ModDiscoverer(versionOverrides, dependencyOverrides);
        discoverer.addCandidateFinder(new DirectoryModCandidateFinder(modsDir, false));
        // In production mode this adds the loader jar itself, which is how the real launcher gets
        // `fabricloader` and the MixinExtras jar nested inside it.
        discoverer.addCandidateFinder(new ClasspathModCandidateFinder());

        Map<String, Set<ModCandidateImpl>> environmentDisabled = new HashMap<>();
        List<ModCandidateImpl> candidates = discoverer.discoverMods(loader, environmentDisabled);

        for (Path path : discoverer.getNonFabricMods()) {
            warnings.add(path + " is not a Fabric mod and is ignored");
        }

        List<ModCandidateImpl> order = List.of();

        try {
            order = ModResolver.resolve(candidates, environment, environmentDisabled);
        } catch (ModResolutionException e) {
            errors.add(cap(e.getMessage()));
        }

        // Add mod jar paths to the system classloader BEFORE loader.load(),
        // so that Mixin's service classloader can resolve mixin config
        // resources (mixins.*.json) and mixin classes when FabricLoader
        // bootstraps Mixin during load().
        addModJarsToSystemClassLoader(candidates, modsDir);

        // Initialize all mods: Fabric Loader loads classes, applies Mixin
        // transformations, and invokes entrypoints. This must be called before
        // any mod code can run.
        loader.load();

        // Initialise Mixin: scan mod jars for mixin configs, register them,
        // then initialise the Mixin environment so that bytecode transformations
        // can be applied when classes are loaded via LOAD_CLASS.
        initialiseMixinEnvironment();

        List<String> disabled = new ArrayList<>();
        for (Set<ModCandidateImpl> set : environmentDisabled.values()) {
            for (ModCandidateImpl candidate : set) {
                disabled.add(candidate.getId());
            }
        }
        Collections.sort(disabled);

        List<ModCandidateImpl> mods = order.isEmpty() ? candidates : order;
        int roots = 0;
        for (ModCandidateImpl candidate : candidates) {
            if (candidate.isRoot()) {
                roots++;
            }
        }

        return report(errors.isEmpty(), gameVersion, side, roots, mods, order, disabled, errors,
                warnings, start);
    }

    public static String failure(String gameVersion, int side, Throwable error) {
        List<String> errors = new ArrayList<>();
        String message = error.getMessage();
        errors.add(cap(error.getClass().getName()
                + (message == null || message.isBlank() ? "" : ": " + message)));

        return report(false, gameVersion, side, 0, List.of(), List.of(), List.of(), errors,
                List.of(), System.nanoTime());
    }

    private static String report(boolean ok, String gameVersion, int side, int roots,
            List<ModCandidateImpl> mods, List<ModCandidateImpl> order, List<String> disabled,
            List<String> errors, List<String> warnings, long start) {
        StringBuilder out = new StringBuilder(64 * 1024);
        out.append('{');
        bool(out, "ok", ok);
        string(out, "loader", FabricLoaderImpl.VERSION);
        string(out, "gameVersion", gameVersion);
        string(out, "side", side == Wire.SIDE_SERVER ? "server" : "client");
        number(out, "javaVersion", Runtime.version().feature());
        number(out, "roots", roots);

        out.append(",\"mods\":[");
        for (int i = 0; i < mods.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            mod(out, mods.get(i));
        }
        out.append(']');

        out.append(",\"order\":[");
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            quote(out, order.get(i).getId());
        }
        out.append(']');

        strings(out, "disabled", disabled);
        strings(out, "errors", errors);
        strings(out, "warnings", warnings);
        number(out, "elapsedMillis", (System.nanoTime() - start) / 1_000_000);
        out.append('}');

        return out.toString();
    }

    private static void mod(StringBuilder out, ModCandidateImpl candidate) {
        LoaderModMetadata meta = candidate.getMetadata();
        out.append('{');
        string(out, "id", meta.getId());
        string(out, "version", meta.getVersion().getFriendlyString());
        string(out, "name", meta.getName());
        string(out, "type", meta.getType());
        string(out, "environment", meta.getEnvironment().name().toLowerCase(Locale.ROOT));
        bool(out, "builtin", candidate.isBuiltin());
        bool(out, "nested", isNested(candidate));

        out.append(",\"origins\":[");
        List<Path> origins = candidate.getOriginPaths();
        if (origins == null) {
            origins = List.of();
        }
        for (int i = 0; i < origins.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            quote(out, origins.get(i).toString());
        }
        out.append(']');

        stringArray(out, "provides", nullToEmpty(meta.getProvides()));
        stringArray(out, "entrypoints", nullToEmpty(meta.getEntrypointKeys()));

        dependencies(out, "depends", meta, ModDependency.Kind.DEPENDS);
        dependencies(out, "recommends", meta, ModDependency.Kind.RECOMMENDS);
        dependencies(out, "suggests", meta, ModDependency.Kind.SUGGESTS);
        dependencies(out, "conflicts", meta, ModDependency.Kind.CONFLICTS);
        dependencies(out, "breaks", meta, ModDependency.Kind.BREAKS);
        out.append('}');
    }

    private static boolean isNested(ModCandidateImpl candidate) {
        Collection<ModCandidateImpl> parents = candidate.getParentMods();
        if (parents != null && !parents.isEmpty()) {
            return true;
        }

        List<Path> origins = candidate.getOriginPaths();
        if (origins == null) {
            origins = List.of();
        }

        for (Path origin : origins) {
            if (origin.toString().contains("!/")) {
                return true;
            }
        }

        return false;
    }

    private static void dependencies(StringBuilder out, String name, LoaderModMetadata meta,
            ModDependency.Kind kind) {
        out.append(",\"").append(name).append("\":[");

        boolean first = true;
        Collection<ModDependency> dependencies = meta.getDependencies();
        if (dependencies == null) {
            dependencies = List.of();
        }

        for (ModDependency dependency : dependencies) {
            if (dependency.getKind() != kind) {
                continue;
            }

            if (!first) {
                out.append(',');
            }
            first = false;

            out.append('{');
            string(out, "id", dependency.getModId());

            out.append(",\"versions\":[");
            Collection<?> requirements = dependency.getVersionRequirements();
            int i = 0;
            for (Object requirement : requirements) {
                if (i++ > 0) {
                    out.append(',');
                }
                quote(out, requirement.toString());
            }
            out.append("]}");
        }

        out.append(']');
    }

    private static List<Path> classPath() {
        List<Path> paths = new ArrayList<>();

        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!entry.isEmpty()) {
                paths.add(Paths.get(entry));
            }
        }

        return paths;
    }

    private static void strings(StringBuilder out, String name, List<String> values) {
        out.append(",\"").append(name).append("\":[");

        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            quote(out, values.get(i));
        }

        out.append(']');
    }

    private static void stringArray(StringBuilder out, String name, Collection<String> values) {
        List<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        strings(out, name, sorted);
    }

    private static void bool(StringBuilder out, String name, boolean value) {
        if (out.length() > 0) {
            char last = out.charAt(out.length() - 1);
            if (last != '{' && last != '[' && last != ',') {
                out.append(',');
            }
        }
        quote(out, name);
        out.append(':').append(value);
    }

    private static void number(StringBuilder out, String name, long value) {
        if (out.length() > 0) {
            char last = out.charAt(out.length() - 1);
            if (last != '{' && last != '[' && last != ',') {
                out.append(',');
            }
        }
        quote(out, name);
        out.append(':').append(value);
    }

    private static void string(StringBuilder out, String name, String value) {
        if (out.length() > 0) {
            char last = out.charAt(out.length() - 1);
            if (last != '{' && last != '[' && last != ',') {
                out.append(',');
            }
        }
        quote(out, name);
        out.append(':');
        quote(out, value);
    }

    private static void quote(StringBuilder out, String value) {
        out.append('"');

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }

        out.append('"');
    }

    private static String cap(String value) {
        if (value == null) {
            return "unknown error";
        }

        return value.length() <= MAX_MESSAGE ? value : value.substring(0, MAX_MESSAGE) + "…";
    }

    /**
     * Add all discovered mod jar paths (plus any jars in the mods directory that
     * Fabric Loader didn't recognise as Fabric mods) to the system classloader.
     *
     * <p>This must happen before {@link FabricLoaderImpl#load()} so that Mixin's
     * service classloader can resolve mixin config resources
     * ({@code mixins.*.json}) and mixin classes from mod jars.
     */
    private static void addModJarsToSystemClassLoader(
            List<ModCandidateImpl> mods, Path modsDir) throws Exception {
        List<Path> modJars = new ArrayList<>();
        for (ModCandidateImpl candidate : mods) {
            try {
                List<Path> paths = candidate.getPaths();
                if (paths != null) {
                    modJars.addAll(paths);
                }
            } catch (IllegalStateException e) {
                // Builtin mods (fabricloader, java, minecraft) don't have paths
            }
        }

        // ALSO scan the mods directory directly for jars that Fabric Loader
        // may not recognise as Fabric mods (e.g. pure mixin-only mods without
        // fabric.mod.json).
        if (Files.isDirectory(modsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
                for (Path jar : stream) {
                    if (!modJars.contains(jar)) {
                        modJars.add(jar);
                    }
                }
            }
        }

        ClassLoader systemCL = ClassLoader.getSystemClassLoader();
        if (systemCL instanceof URLClassLoader urlCL) {
            Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
            for (Path jar : modJars) {
                if (Files.isRegularFile(jar) && jar.toString().endsWith(".jar")) {
                    addURL.invoke(urlCL, jar.toUri().toURL());
                }
            }
        }

        // Also set the thread context classloader to include mod jars, so that
        // Mixin's MixinEnvironment can find config files and mixin classes.
        ClassLoader modCL = new URLClassLoader(
                modJars.stream()
                        .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".jar"))
                        .map(p -> {
                            try { return p.toUri().toURL(); } catch (Exception e) { return null; }
                        })
                        .filter(u -> u != null)
                        .toArray(URL[]::new),
                ClassLoader.getSystemClassLoader());
        Thread.currentThread().setContextClassLoader(modCL);
    }

    /**
     * Initialise the Mixin environment so that
     * {@link org.spongepowered.asm.mixin.transformer.IMixinTransformer#transformClassBytes}
     * works when classes are loaded via {@link Main#loadClass}.
     *
     * <p>Mixin configuration registration is handled by Fabric Loader itself
     * during {@link FabricLoaderImpl#load()} — it reads each mod's
     * {@code fabric.mod.json} and registers the declared mixin configs via
     * {@code FabricMixinBootstrap.init()}. This method only ensures the
     * environment is initialised.
     */
    private static void initialiseMixinEnvironment() throws Exception {
        MixinBootstrap.init();
        try {
            MixinEnvironment.init(MixinEnvironment.Phase.DEFAULT);
        } catch (Throwable t) {
            try {
                if (MixinEnvironment.getDefaultEnvironment() == null) {
                    throw t;
                }
            } catch (Throwable ignored) {
                throw t;
            }
            System.err.println("horizon host: Mixin environment was already initialised");
        }
        System.err.println("horizon host: Mixin environment initialised");
    }

    private static <T> Collection<T> nullToEmpty(Collection<T> value) {
        return value == null ? List.of() : value;
    }

    private Discovery() {
    }
}
