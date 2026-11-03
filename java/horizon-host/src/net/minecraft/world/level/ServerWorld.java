package net.minecraft.world.level;

/**
 * Minimal compatibility shim for Minecraft's ServerWorld.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class ServerWorld extends Level {
    public ServerWorld() {
        super("overworld");
    }

    public ServerWorld(String dimension) {
        super(dimension);
    }

    @Override
    public boolean isClientSide() {
        return false;
    }
}
