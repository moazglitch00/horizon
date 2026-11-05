package net.minecraft.client.multiplayer;

import net.minecraft.world.level.Level;

/**
 * Minimal compatibility shim for Minecraft's ClientLevel (formerly ClientWorld).
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class ClientLevel extends Level {
    public ClientLevel() {
        super("overworld");
    }

    public ClientLevel(String dimension) {
        super(dimension);
    }

    @Override
    public boolean isClientSide() {
        return true;
    }
}
