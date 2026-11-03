package net.minecraft.core;

/**
 * Minimal compatibility shim for Minecraft's BlockPos.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public record BlockPos(int x, int y, int z) {
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @Override
    public String toString() {
        return "[" + x + ", " + y + ", " + z + "]";
    }
}