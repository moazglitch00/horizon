package net.minecraft.world.level.block.state;

/**
 * Compatibility shim for Minecraft's RenderShape.
 * Controls how a block state is rendered.
 */
public enum RenderShape {
    MODEL,
    INVISIBLE,
    ENTITYBLOCK_ANIMATED,
    ENTITYBLOCK_STATIC;

    public boolean isFullBlock() {
        return this == MODEL;
    }

    public boolean isFullBlock(net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
        return isFullBlock();
    }
}
