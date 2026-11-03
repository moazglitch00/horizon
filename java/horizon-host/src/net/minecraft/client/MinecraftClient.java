package net.minecraft.client;

/**
 * Minimal compatibility shim for MinecraftClient.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class MinecraftClient {
    private static MinecraftClient instance;

    private MinecraftClient() {
    }

    public static MinecraftClient getInstance() {
        if (instance == null) {
            instance = new MinecraftClient();
        }
        return instance;
    }

    public void setInstance(MinecraftClient client) {
        instance = client;
    }

    public String getVersion() {
        return "26.3";
    }

    @Override
    public String toString() {
        return "MinecraftClient";
    }
}
