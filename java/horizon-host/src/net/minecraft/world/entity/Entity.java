package net.minecraft.world.entity;

import net.minecraft.util.Identifier;

/**
 * Minimal compatibility shim for Minecraft's Entity.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class Entity {
    private final Identifier type;
    private double x, y, z;
    private float yaw, pitch;
    private boolean removed = false;

    public Entity(Identifier type) {
        this.type = type;
    }

    public Identifier getType() {
        return type;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }

    public void setPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void remove() {
        this.removed = true;
    }

    public void tick() {
        // Override in subclasses
    }

    @Override
    public String toString() {
        return "Entity[" + (type != null ? type.toString() : "unknown") + "]";
    }
}
