package net.minecraft.world.item;

/**
 * Minimal compatibility shim for Minecraft's ItemStack.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class ItemStack {
    private final Item item;
    private int count;

    public ItemStack(Item item) {
        this(item, 1);
    }

    public ItemStack(Item item, int count) {
        this.item = item;
        this.count = count;
    }

    public Item getItem() {
        return item;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public boolean isEmpty() {
        return item == null || count <= 0;
    }

    public static ItemStack EMPTY = new ItemStack(null, 0);

    @Override
    public String toString() {
        return "ItemStack[" + item + " x" + count + "]";
    }
}
