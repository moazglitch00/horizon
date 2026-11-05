package net.minecraft.server.level;

import net.minecraft.world.level.Level;

/**
 * Minimal compatibility shim for Minecraft's ServerLevel (formerly ServerWorld).
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class ServerLevel extends Level {
    public ServerLevel() {
        super("overworld");
    }

    public ServerLevel(String dimension) {
        super(dimension);
    }

    @Override
    public boolean isClientSide() {
        return false;
    }
}
