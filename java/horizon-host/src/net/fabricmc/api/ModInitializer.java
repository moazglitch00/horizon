package net.fabricmc.api;

/**
 * Fabric Loader's main entrypoint interface.
 *
 * <p>Real signature from Fabric Loader: {@code void onInitialize()}. A mod that
 * is instantiated by the host must expose a public no-argument constructor.
 */
public interface ModInitializer {
    void onInitialize();
}
