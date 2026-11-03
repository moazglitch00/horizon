package net.minecraft.world.level;

/**
 * Minimal compatibility shim for Minecraft's ClientWorld.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class ClientWorld extends Level {
    public ClientWorld() {
        super("overworld");
    }

    public ClientWorld(String dimension) {
        super(dimension);
    }

    @Override
    public boolean isClientSide() {
        return true;
    }
}
