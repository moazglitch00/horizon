package net.minecraft.world.level;

/**
 * Minimal compatibility shim for Minecraft's Level (World).
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class Level {
    private final String dimension;

    public Level(String dimension) {
        this.dimension = dimension;
    }

    public String getDimension() {
        return dimension;
    }

    public boolean isClientSide() {
        return false;
    }

    @Override
    public String toString() {
        return "Level[" + dimension + "]";
    }
}
