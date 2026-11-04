package net.minecraft.server;

/**
 * Minimal compatibility shim for MinecraftServer.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class MinecraftServer {
    private static MinecraftServer instance;
    private volatile boolean running;

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

    /**
     * Whether the engine has started this server. The shim is not a running
     * server, so this is false until the Rust side reports a start.
     */
    public boolean isRunning() {
        return running;
    }

    /** Called by the bridge when the Rust engine starts/stops the server. */
    public void setRunning(boolean running) {
        this.running = running;
    }

    public void tick() {
        throw new UnsupportedOperationException(
                "server-tick: MinecraftServer.tick is owned by the Rust engine and is not "
                        + "bridged to the JVM host yet (fabric-runtime Phase 6)");
    }

    @Override
    public String toString() {
        return "MinecraftServer";
    }
}
