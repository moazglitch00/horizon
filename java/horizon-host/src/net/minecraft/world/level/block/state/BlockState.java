package net.minecraft.world.level.block.state;

import net.minecraft.world.level.block.Block;

/**
 * Minimal compatibility shim for Minecraft's BlockState.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class BlockState {
    private final Block block;
    private final int flags;

    public BlockState(Block block) {
        this(block, 0);
    }

    public BlockState(Block block, int flags) {
        this.block = block;
        this.flags = flags;
    }

    public Block getBlock() {
        return block;
    }

    public int getFlags() {
        return flags;
    }

    @Override
    public String toString() {
        return "BlockState[" + (block != null ? block.toString() : "null") + "]";
    }
}
