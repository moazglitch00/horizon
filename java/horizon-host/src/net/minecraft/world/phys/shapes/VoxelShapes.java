package net.minecraft.world.phys.shapes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.VoxelShape;

/**
 * Compatibility shim for Minecraft's VoxelShapes.
 * Factory for creating voxel shapes.
 */
public final class VoxelShapes {
    public static final VoxelShape block = net.minecraft.world.level.material.VoxelShape.block();
    public static final VoxelShape empty = net.minecraft.world.level.material.VoxelShape.empty();

    private VoxelShapes() {}

    public static VoxelShape block() {
        return block;
    }

    public static VoxelShape empty() {
        return empty;
    }

    public static VoxelShape create(net.minecraft.world.phys.AABB box) {
        return net.minecraft.world.level.material.VoxelShape.create(box);
    }

    public static VoxelShape fullCube() {
        return block();
    }

    public static VoxelShape column(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return net.minecraft.world.level.material.VoxelShape.box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static VoxelShape shapes(net.minecraft.world.level.material.VoxelShape... shapes) {
        VoxelShape result = net.minecraft.world.level.material.VoxelShape.empty();
        for (VoxelShape shape : shapes) {
            result = result.merge(shape);
        }
        return result;
    }

    public static boolean isFullBlock(net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
        return false;
    }

    public static boolean faceAbsent(net.minecraft.world.level.material.VoxelShape shape, net.minecraft.core.Direction direction) {
        return shape.isEmpty();
    }
}
