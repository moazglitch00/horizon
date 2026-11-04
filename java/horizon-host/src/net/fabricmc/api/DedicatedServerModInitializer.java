package net.fabricmc.api;

/**
 * Fabric Loader's dedicated-server entrypoint interface.
 *
 * <p>Real signature from Fabric Loader: {@code void onInitializeServer()}.
 */
public interface DedicatedServerModInitializer {
    void onInitializeServer();
}
