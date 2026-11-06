package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Compatibility shim for Minecraft's Level (World).
 *
 * <p>This is the central world access class that Fabric mods use constantly.
 * Missing methods are the #1 cause of NoSuchMethodError in real mods.
 */
public class Level {
    private final String dimension;

    public Level(String dimension) {
        this.dimension = dimension;
    }

    public String getDimension() {
        return dimension;
    }

    public boolean isClientSide() {
        return false;
    }

    /* ========================================================================
     * Block state access - CRITICAL for any block-interacting mod
     */

    /** Get the block state at a position. Fabric mods call this constantly. */
    public BlockState getBlockState(BlockPos pos) {
        return Block.AIR.getDefaultState();
    }

    /** Set the block state at a position with flags. */
    public boolean setBlockState(BlockPos pos, BlockState state, int flags) {
        return false;
    }

    /** Set the block state at a position (default flags). */
    public boolean setBlockState(BlockPos pos, BlockState state) {
        return setBlockState(pos, state, 3);
    }

    /** Get the block (not state) at a position. */
    public Block getBlock(BlockPos pos) {
        return getBlockState(pos).getBlock();
    }

    /** Check if a position is air. */
    public boolean isAir(BlockPos pos) {
        return getBlockState(pos).isAir();
    }

    /* ========================================================================
     * Block entity access
     */

    /** Get the block entity at a position. */
    public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    /* ========================================================================
     * Light access
     */

    /** Get the best light level at a position (sky + block). */
    public int getBestLightLevel(BlockPos pos) {
        return 15;
    }

    /** Get the sky light level at a position. */
    public int getBrightness(BlockPos pos) {
        return 15;
    }

    /* ========================================================================
     * Line of sight
     */

    /** Check if there's a clear line of sight between two entities. */
    public boolean hasLineOfSight(Entity start, Entity target) {
        return true;
    }

    /** Check if there's a clear line of sight from start position to target. */
    public boolean unblockedLine(BlockHitResult trace, Vec3 target) {
        return true;
    }

    /* ========================================================================
     * Random ticking
     */

    /** Random tick at a position (used for crop growth, etc.). */
    public void randomTick(BlockPos pos, net.minecraft.util.RandomSource random) {
        // Default: no-op
    }

    /** Schedule a block tick. */
    public void scheduleTick(BlockPos pos, Block block, int delay) {
        // Default: no-op
    }

    /* ========================================================================
     * Random access
     */

    /** Get the level's random source. */
    public net.minecraft.util.RandomSource getRandom() {
        return net.minecraft.util.RandomSource.create();
    }

    /* ========================================================================
     * Height access
     */

    /** Get the height of the highest non-air block at a position. */
    public int getHeight(int x, int z) {
        return 64;
    }

    /** Get the highest block state at a position. */
    public BlockState getHighestState(BlockPos pos) {
        return Block.AIR.getDefaultState();
    }

    /* ========================================================================
     * Chunk access
     */

    /** Get the chunk at a position. */
    public LevelChunk getChunk(BlockPos pos) {
        return null;
    }

    /** Get the chunk at a position (non-null variant). */
    public LevelChunk getChunkAt(BlockPos pos) {
        return getChunk(pos);
    }

    /* ========================================================================
     * Game time
     */

    /** Get the game time (ticks). */
    public long getGameTime() {
        return 0;
    }

    /** Get the day time (ticks, affected by /time). */
    public long getDayTime() {
        return 0;
    }

    /* ========================================================================
     * Difficulty
     */

    /** Get the difficulty. */
    public String getDifficulty() {
        return "normal";
    }

    /* ========================================================================
     * Game rules
     */

    /** Get the game rules. */
    public Object getGameRules() {
        return null;
    }

    /* ========================================================================
     * Entity access - CRITICAL for entity-mods
     */

    /** Add a fresh entity to the world. */
    public boolean addFreshEntity(Entity entity) {
        return false;
    }

    /** Remove an entity from the world. */
    public void removeEntity(Entity entity) {
        // Default: no-op
    }

    /** Get all entities in an AABB. */
    public List<Entity> getEntities(AABB box) {
        return new ArrayList<>();
    }

    /** Get entities of a specific class in an AABB. */
    public <T extends Entity> List<T> getEntitiesOfClass(AABB box, java.lang.Class<T> clazz) {
        return new ArrayList<>();
    }

    /** Get all players. */
    public List<Player> getPlayers() {
        return new ArrayList<>();
    }

    /** Get all players (with predicate). */
    public List<Player> getPlayers(Predicate<? super Player> predicate) {
        return new ArrayList<>();
    }

    /* ========================================================================
     * Server access
     */

    /** Get the server this level belongs to. */
    public net.minecraft.server.MinecraftServer getServer() {
        return null;
    }

    @Override
    public String toString() {
        return "Level[" + dimension + "]";
    }
}
