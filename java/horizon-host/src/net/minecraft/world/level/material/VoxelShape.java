package net.minecraft.world.level.material;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Compatibility shim for Minecraft's VoxelShape.
 * Represents the collision shape of a block state.
 */
public class VoxelShape {
    private final AABB bounds;

    private VoxelShape(AABB bounds) {
        this.bounds = bounds;
    }

    public static VoxelShape empty() {
        return new VoxelShape(new AABB(0, 0, 0, 0, 0, 0));
    }

    public static VoxelShape block() {
        return new VoxelShape(new AABB(0, 0, 0, 1, 1, 1));
    }

    public static VoxelShape box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new VoxelShape(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
    }

    public static VoxelShape create(AABB box) {
        return new VoxelShape(box);
    }

    public AABB getBounds() {
        return bounds;
    }

    public boolean isEmpty() {
        return bounds == null || bounds.equals(new AABB(0, 0, 0, 0, 0, 0));
    }

    public VoxelShape merge(VoxelShape other) {
        if (isEmpty()) return other;
        if (other.isEmpty()) return this;
        return block(); // Simplified merge
    }

    public boolean equals(VoxelShape other) {
        return other != null && bounds.equals(other.bounds);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        VoxelShape other = (VoxelShape) obj;
        return equals(other);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(bounds);
    }

    @Override
    public String toString() {
        return "VoxelShape[" + bounds + "]";
    }
}
