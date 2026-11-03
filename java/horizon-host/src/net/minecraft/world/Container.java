package net.minecraft.world;

import net.minecraft.world.item.ItemStack;

/**
 * Minimal compatibility shim for Minecraft's Container (Inventory).
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public interface Container {
    int getContainerSize();

    ItemStack getItem(int slot);

    void setItem(int slot, ItemStack stack);

    default boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    default ItemStack removeItem(int slot, int amount) {
        ItemStack stack = getItem(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int toRemove = Math.min(amount, stack.getCount());
        ItemStack result = new ItemStack(stack.getItem(), toRemove);
        stack.setCount(stack.getCount() - toRemove);
        if (stack.getCount() <= 0) {
            setItem(slot, ItemStack.EMPTY);
        } else {
            setItem(slot, stack);
        }
        return result;
    }

    default ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = getItem(slot);
        setItem(slot, ItemStack.EMPTY);
        return stack;
    }

    default void setChanged() {
        // Override in implementations if needed
    }

    default void clearContent() {
        for (int i = 0; i < getContainerSize(); i++) {
            setItem(i, ItemStack.EMPTY);
        }
    }

    default boolean stillValid(Object player) {
        return true;
    }
}
