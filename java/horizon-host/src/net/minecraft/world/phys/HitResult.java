package net.minecraft.world.phys;

import net.minecraft.world.entity.Entity;

/**
 * Compatibility shim for Minecraft's HitResult: the base type of every
 * confirmed ray hit.
 *
 * <p>{@link #distanceTo(Entity)} is squared distance, matching vanilla's
 * bytecode: it never takes the square root and never returns a length.
 */
public abstract class HitResult {
    protected final Vec3 location;

    protected HitResult(Vec3 location) {
        this.location = location;
    }

    /** Squared distance between the hit point and the entity's position. */
    public double distanceTo(Entity entity) {
        return (this.location.x - entity.getX()) * (this.location.x - entity.getX())
            + (this.location.y - entity.getY()) * (this.location.y - entity.getY())
            + (this.location.z - entity.getZ()) * (this.location.z - entity.getZ());
    }

    public abstract Type getType();

    public Vec3 getLocation() {
        return this.location;
    }

    public enum Type {
        MISS,
        BLOCK,
        ENTITY;
    }
}
