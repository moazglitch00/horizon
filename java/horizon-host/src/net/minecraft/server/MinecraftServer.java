package net.minecraft.server;

/**
 * Minimal compatibility shim for MinecraftServer.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class MinecraftServer {
    private static MinecraftServer instance;

    private MinecraftServer() {
    }

    public static MinecraftServer getInstance() {
        if (instance == null) {
            instance = new MinecraftServer();
        }
        return instance;
    }

    public String getVersion() {
        return "26.3";
    }

    public boolean isRunning() {
        return true;
    }

    public void tick() {
        // Override in real implementation
    }

    @Override
    public String toString() {
        return "MinecraftServer";
    }
}
