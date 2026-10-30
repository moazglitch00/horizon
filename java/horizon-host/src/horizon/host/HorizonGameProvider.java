package horizon.host;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.util.Arguments;

/**
 * The game, as Fabric Loader sees it during a discovery probe.
 *
 * <p>Loader 0.19.5 cannot discover mods without a {@code GameProvider}: {@code ModDiscoverer}
 * calls {@code getBuiltinMods()} and {@code FabricLoaderImpl} takes the game directory from here
 * (verified with {@code javap} and the 0.19.5 source, see
 * {@code docs/fabric-compatibility/ROADMAP.md} phase 2). This provider supplies exactly what
 * discovery needs:
 *
 * <ul>
 *   <li>the game id ({@code minecraft}) and version, so mods that depend on {@code minecraft}
 *       resolve the same way they do under the real launcher;
 *   <li>the game directory, which Loader turns into {@code <game>/config};
 *   <li>one builtin mod for the game itself. Fabric Loader and MixinExtras are not builtins here:
 *       they arrive as ordinary candidates through {@code ClasspathModCandidateFinder}, which is
 *       how the real launcher discovers them too.
 * </ul>
 *
 * <p>There is no Minecraft Java jar in Horizon: the engine is Rust and authoritative. The game's
 * builtin mod therefore has no code path. Launching, class-path unlocking and entrypoint patching
 * are Phase 6 work and throw a named exception instead of returning a plausible lie.
 */
public final class HorizonGameProvider implements GameProvider {
    private final Path gameDir;
    private final String gameVersion;
    private final List<Path> gamePaths;

    public HorizonGameProvider(Path gameDir, String gameVersion, List<Path> gamePaths) {
        this.gameDir = gameDir;
        this.gameVersion = gameVersion;
        this.gamePaths = List.copyOf(gamePaths);
    }

    @Override
    public String getGameId() {
        return "minecraft";
    }

    @Override
    public String getGameName() {
        return "Minecraft";
    }

    @Override
    public String getRawGameVersion() {
        return gameVersion;
    }

    @Override
    public String getNormalizedGameVersion() {
        return gameVersion;
    }

    @Override
    public Collection<BuiltinMod> getBuiltinMods() {
        ModMetadata metadata = new BuiltinModMetadata.Builder(getGameId(), getNormalizedGameVersion())
                .setName(getGameName())
                .setDescription("Horizon's native engine, reported to Fabric Loader for mod discovery")
                .build();
        return Collections.singletonList(new BuiltinMod(gamePaths, metadata));
    }

    @Override
    public String getEntrypoint() {
        return "net.minecraft.client.main.Main";
    }

    @Override
    public Path getLaunchDirectory() {
        return gameDir;
    }

    @Override
    public boolean requiresUrlClassLoader() {
        return false;
    }

    @Override
    public Set<BuiltinTransform> getBuiltinTransforms(String className) {
        // Horizon models no bytecode transforms in a discovery probe. Phase 6 registers them.
        return Collections.emptySet();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean locateGame(FabricLauncher launcher, String[] args) {
        throw launchOnly("locateGame");
    }

    @Override
    public void initialize(FabricLauncher launcher) {
        throw launchOnly("initialize");
    }

    @Override
    public GameTransformer getEntrypointTransformer() {
        throw launchOnly("getEntrypointTransformer");
    }

    @Override
    public void unlockClassPath(FabricLauncher launcher) {
        throw launchOnly("unlockClassPath");
    }

    @Override
    public void launch(ClassLoader loader) {
        throw launchOnly("launch");
    }

    @Override
    public Arguments getArguments() {
        // Loader asks for these when building a launch command; a probe has not parsed any.
        return new Arguments();
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        throw launchOnly("getLaunchArguments");
    }

    @Override
    public boolean canOpenErrorGui() {
        return false;
    }

    @Override
    public boolean hasAwtSupport() {
        return false;
    }

    private static UnsupportedOperationException launchOnly(String method) {
        return new UnsupportedOperationException("HorizonGameProvider." + method
                + " belongs to launching, which is roadmap phase 6; this host only runs Loader"
                + " discovery (phase 2)");
    }
}
