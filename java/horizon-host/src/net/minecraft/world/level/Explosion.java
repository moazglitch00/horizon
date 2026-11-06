package net.minecraft.world.level;

import net.minecraft.core.BlockPos;

/**
 * Compatibility shim for Minecraft's Explosion.
 */
public class Explosion {
    private final Level level;
    private final double x, y, z;

    public Explosion(Level level, double x, double y, double z) {
        this.level = level;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Level getLevel() {
        return level;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }
}
