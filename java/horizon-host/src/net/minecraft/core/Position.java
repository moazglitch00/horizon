package net.minecraft.core;

/**
 * Compatibility shim for Minecraft's Position: a point in 3D space.
 * Implemented by {@link Vec3i} and {@link net.minecraft.world.phys.Vec3}.
 */
public interface Position {
    double x();

    double y();

    double z();
}
