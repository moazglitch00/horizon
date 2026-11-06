package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compatibility shim for Minecraft's BlockGetter interface.
 * Provides read-only access to block states in the world.
 */
public interface BlockGetter {
    /** Get the block state at a position. */
    BlockState getBlockState(BlockPos pos);

    /** Get the block entity at a position. */
    net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos);
}
