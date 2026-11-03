package net.minecraft.world.level.chunk;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal compatibility shim for Minecraft's LevelChunk.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class LevelChunk {
    private final Level level;
    private final ChunkPos pos;
    private final Map<String, BlockState> blockStates = new HashMap<>();

    public LevelChunk(Level level, ChunkPos pos) {
        this.level = level;
        this.pos = pos;
    }

    public Level getLevel() {
        return level;
    }

    public ChunkPos getPos() {
        return pos;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public BlockState getBlockState(int x, int y, int z) {
        BlockState state = blockStates.get(key(x, y, z));
        if (state == null) {
            return new BlockState(new Block(Block.Properties.of()));
        }
        return state;
    }

    public void setBlockState(int x, int y, int z, BlockState state) {
        if (state == null) {
            blockStates.remove(key(x, y, z));
        } else {
            blockStates.put(key(x, y, z), state);
        }
    }

    @Override
    public String toString() {
        return "LevelChunk[" + (pos != null ? pos.toString() : "null") + "]";
    }
}
