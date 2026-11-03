package net.minecraft.world.level.block.entity;

/**
 * Minimal compatibility shim for Minecraft's BlockEntity.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class BlockEntity {
    private final BlockEntityType<?> type;
    private int x, y, z;
    private boolean removed = false;

    public BlockEntity(BlockEntityType<?> type) {
        this(type, 0, 0, 0);
    }

    public BlockEntity(BlockEntityType<?> type, int x, int y, int z) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public BlockEntityType<?> getType() {
        return type;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public void setPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void setRemoved() {
        this.removed = true;
    }

    public void save(Object tag) {
        saveAdditional(tag);
    }

    public void load(Object tag) {
        loadAdditional(tag);
    }

    protected void saveAdditional(Object tag) {
        // Override in subclasses
    }

    protected void loadAdditional(Object tag) {
        // Override in subclasses
    }

    public void tick() {
        // Override in subclasses
    }

    @Override
    public String toString() {
        return "BlockEntity[" + (type != null ? type.toString() : "unknown") + " @ " + x + "," + y + "," + z + "]";
    }
}