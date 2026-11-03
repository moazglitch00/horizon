package net.minecraft.world.level;

/**
 * Minimal compatibility shim for Minecraft's ChunkPos.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public record ChunkPos(int x, int z) {
    public long asLong() {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    @Override
    public String toString() {
        return "[" + x + ", " + z + "]";
    }
}
