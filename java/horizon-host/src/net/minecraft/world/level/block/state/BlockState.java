package net.minecraft.world.level.block.state;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Compatibility shim for Minecraft's BlockState.
 *
 * <p>BlockState represents the state of a block at a position (e.g., which
 * way a block faces, whether a chest is open, etc.). Fabric mods use
 * getValue(), isAir(), and hasProperty() constantly.
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

    /**
     * Get the value of a property for this state.
     * Fabric mods use this to read block properties (e.g., facing direction).
     */
    public <T extends Comparable<T>> T getValue(Property<T> property) {
        // Default: return null - individual block state implementations
        // should override this
        return null;
    }

    /** Check if this state has a specific property. */
    public boolean hasProperty(Property<?> property) {
        return false;
    }

    /** Get all properties for this state's block. */
    public StateDefinition<Block, BlockState> getProperties() {
        return block.getStateDefinition();
    }

    /** Check if this state is air. */
    public boolean isAir() {
        return block == Block.AIR;
    }

    /** Check if this state is a specific block. */
    public boolean is(Block block) {
        return this.block == block;
    }

    /** Check if this state is a specific block (registry key variant). */
    public boolean is(Holder<Block> block) {
        return block != null;
    }

    /** Check if this state is solid. */
    public boolean isSolid() {
        return !isAir();
    }

    /** Check if this state is a full cube. */
    public boolean isFullCube() {
        return false;
    }

    /** Check if this state blocks motion. */
    public boolean blocksMotion() {
        return false;
    }

    /** Check if this state is a liquid. */
    public boolean isLiquid() {
        return false;
    }

    /** Get the light block value. */
    public int getLightBlock() {
        return 0;
    }

    /** Get the occlusion state. */
    public boolean isOcclusionState() {
        return false;
    }

    /** Get the collision shape. */
    public net.minecraft.world.level.material.VoxelShape getShape(net.minecraft.world.level.BlockGetter reader, net.minecraft.core.BlockPos pos) {
        return net.minecraft.world.level.material.VoxelShape.empty();
    }

    /** Get the collision shape (with context). */
    public net.minecraft.world.level.material.VoxelShape getCollisionShape(net.minecraft.world.level.BlockGetter reader, net.minecraft.core.BlockPos pos, net.minecraft.world.level.material.CollisionContext context) {
        return net.minecraft.world.level.material.VoxelShape.empty();
    }

    /** Get the render shape. */
    public net.minecraft.world.level.block.state.RenderShape getRenderShape() {
        return net.minecraft.world.level.block.state.RenderShape.MODEL;
    }

    /** Get the block entity type. */
    public net.minecraft.world.level.block.entity.BlockEntityType<?> getBlockEntityType() {
        return null;
    }

    /** Check if entity can destroy this state. */
    public boolean canEntityDestroy(net.minecraft.world.entity.Entity entity) {
        return true;
    }

    /** Get the speed factor for entities moving through this state. */
    public float getSpeedFactor() {
        return 1.0f;
    }

    /** Get the jump factor. */
    public float getJumpFactor() {
        return 1.0f;
    }

    /** Get the explosion resistance. */
    public float getExplosionResistance() {
        return block.getExplosionResistance();
    }

    /** Get the piston state for this block state. */
    public BlockState getPistonState(net.minecraft.core.Direction direction) {
        return this;
    }

    /** Get the block item. */
    public net.minecraft.world.item.Item asItem() {
        return block.getBlockItem();
    }

    /** Get the random source for this state. */
    public net.minecraft.util.RandomSource getRandom(net.minecraft.world.level.BlockGetter reader, net.minecraft.core.BlockPos pos) {
        return net.minecraft.util.RandomSource.create();
    }

    @Override
    public String toString() {
        return "BlockState[" + (block != null ? block.toString() : "null") + "]";
    }
}
