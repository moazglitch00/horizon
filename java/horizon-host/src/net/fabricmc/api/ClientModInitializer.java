package net.fabricmc.api;

/**
 * Fabric Loader's client entrypoint interface.
 *
 * <p>Real signature from Fabric Loader: {@code void onInitializeClient()}.
 */
public interface ClientModInitializer {
    void onInitializeClient();
}
