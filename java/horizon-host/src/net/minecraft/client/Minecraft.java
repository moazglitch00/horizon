package net.minecraft.client;

/**
 * Minimal compatibility shim for Minecraft's client entry point
 * ({@code net.minecraft.client.Minecraft}).
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class Minecraft {
    private static Minecraft instance;

    private Minecraft() {
    }

    public static Minecraft getInstance() {
        if (instance == null) {
            instance = new Minecraft();
        }
        return instance;
    }

    public void setInstance(Minecraft client) {
        instance = client;
    }

    public String getVersion() {
        return "26.3";
    }

    @Override
    public String toString() {
        return "Minecraft";
    }
}
