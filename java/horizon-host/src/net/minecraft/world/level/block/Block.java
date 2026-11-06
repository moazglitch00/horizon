package net.minecraft.world.level.block;

import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.server.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.VoxelShape;
import net.minecraft.world.level.material.CollisionContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShapes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.state.RenderShape;
import net.minecraft.core.BlockPos;

/**
 * Compatibility shim for Minecraft's Block.
 *
 * <p>Block is one of the most important classes for Fabric mods.
 * Block mods need getDefaultState(), getStateDefinition(), getBlockItem(),
 * useWithItem(), useWithoutItem(), and many other methods.
 */
public class Block {
    public static final Block AIR = new Block(Properties.of()).setIdAndReturn(Identifier.fromNamespaceAndPath("minecraft", "air"));

    private Identifier id;
    private final Properties properties;

    public Block(Properties properties) {
        this.id = null;
        this.properties = properties;
    }

    public Identifier getId() {
        return id;
    }

    public void setId(Identifier id) {
        this.id = id;
    }

    public Block setIdAndReturn(Identifier id) {
        setId(id);
        return this;
    }

    public Properties getProperties() {
        return properties;
    }

    /* ========================================================================
     * Block state - CRITICAL for block mods
     */

    /** Get the default block state. */
    public BlockState getDefaultState() {
        return new BlockState(this);
    }

    /** Get the state definition. */
    public StateDefinition<Block, BlockState> getStateDefinition() {
        return new StateDefinition<>(this);
    }

    /* ========================================================================
     * Block item
     */

    /** Get the item form of this block (can return null). */
    public Item asItem() {
        return null;
    }

    /** Get the block item. */
    public Item getBlockItem() {
        return null;
    }

    /* ========================================================================
     * Rendering
     */

    /** Get the render shape for a state. */
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Get the map color for a state. */
    public MapColor getMapColor(BlockState state) {
        return MapColor.STONE;
    }

    /** Get the light emission for a state. */
    public int getLightEmission(BlockState state, BlockGetter reader, BlockPos pos) {
        return 0;
    }

    /* ========================================================================
     * Collision
     */

    /** Get the collision shape for a state. */
    public VoxelShape getCollisionShape(BlockState state, BlockGetter reader, BlockPos pos, CollisionContext context) {
        return VoxelShapes.block();
    }

    /** Get the shape for a state. */
    public VoxelShape getShape(BlockState state, BlockGetter reader, BlockPos pos) {
        return VoxelShapes.block();
    }

    /** Get the interaction range. */
    public double getInteractionRange() {
        return 4.5;
    }

    /* ========================================================================
     * Item interaction - CRITICAL for item/block mods
     */

    /** Use block without item (e.g., right-click with empty hand). */
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    /** Use block with item (e.g., right-click with item in hand). */
    public InteractionResult useWithItem(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    /** Get the seed for random operations. */
    public long getSeed(BlockState state) {
        return 0;
    }

    /** Get the state for placement. */
    public BlockState getStateForPlacement(BlockState state, BlockGetter reader, BlockPos pos, Player player, ItemStack stack) {
        return state;
    }

    /** Check if this state has an offset type. */
    public boolean hasOffsetType(BlockState state) {
        return false;
    }

    /** Get the offset type. */
    public OffsetType getOffsetType(BlockState state) {
        return null;
    }

    /* ========================================================================
     * Survival
     */

    /** Check if this block can survive at a position. */
    public boolean canSurvive(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    /** Update shape when neighbor changes. */
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state;
    }

    /** Get destroy progress. */
    public float getDestroyProgress(Player player, BlockState state, Level level, BlockPos pos) {
        return 1.0f;
    }

    /** Get default destroy time. */
    public float defaultDestroyTime() {
        return 1.0f;
    }

    /* ========================================================================
     * Properties
     */

    /** Check if block is luminous. */
    public boolean isLuminous(BlockState state) {
        return false;
    }

    /** Check if block is a redstone conductor. */
    public boolean isRedstoneConductor(BlockState state) {
        return false;
    }

    /** Check if block is a signal source. */
    public boolean isSignalSource(BlockState state) {
        return false;
    }

    /** Get max stack size. */
    public int getMaxStackSize() {
        return 64;
    }

    /** Check if block is valid spawn. */
    public boolean isValidSpawn(BlockState state, BlockGetter reader, BlockPos pos, MobCategory category) {
        return false;
    }

    /** Check if block is randomly ticking. */
    public boolean isRandomlyTicking(BlockState state) {
        return false;
    }

    /** Random tick. */
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Default: no-op
    }

    /** Tick. */
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Default: no-op
    }

    /** Neighbor changed. */
    public void neighborChanged(BlockState state, BlockGetter reader, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean flag) {
        // Default: no-op
    }

    /** Was exploded. */
    public void wasExploded(Level level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        // Default: no-op
    }

    /** On remove. */
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean flag) {
        // Default: no-op
    }

    /** On place. */
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean flag) {
        // Default: no-op
    }

    /** Get explosion resistance. */
    public float getExplosionResistance() {
        return properties.getStrength();
    }

    /** Get expansion. */
    public float getExpansion() {
        return 1.0f;
    }

    /** Get suppression level. */
    public float getSuppressionLevel() {
        return 1.0f;
    }

    @Override
    public String toString() {
        return "Block[" + (id != null ? id.toString() : "unregistered") + "]";
    }

    /* ========================================================================
     * Inner classes needed by Block
     */

    /**
     * Properties for block creation.
     */
    public static class Properties {
        private float strength = 1.0f;
        private boolean requiresTool = false;

        public static Properties of() {
            return new Properties();
        }

        public Properties strength(float strength) {
            this.strength = strength;
            return this;
        }

        public Properties requiresCorrectToolForDrops() {
            this.requiresTool = true;
            return this;
        }

        public float getStrength() {
            return strength;
        }

        public boolean requiresTool() {
            return requiresTool;
        }
    }

    /**
     * LevelAccessor interface - implemented by both Level and ServerLevel.
     */
    public interface LevelAccessor extends BlockGetter {
        boolean setBlockState(BlockPos pos, BlockState state, int flags);
    }

    /**
     * OffsetType enum.
     */
    public enum OffsetType {
        NONE,
        XZ,
        XZY
    }
}
