package net.minecraft.world.level.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minimal compatibility shim for Minecraft's ChunkAccess.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public interface ChunkAccess {
    BlockState getBlockState(BlockPos pos);

    BlockState setBlockState(BlockPos pos, BlockState state);

    int getHeight();
}