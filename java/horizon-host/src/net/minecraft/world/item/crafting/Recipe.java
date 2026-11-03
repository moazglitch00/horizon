package net.minecraft.world.item.crafting;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Minimal compatibility shim for Minecraft's Recipe.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public interface Recipe {
    ItemStack getResultItem();

    default ItemStack getResultItem(Object registryAccess) {
        return getResultItem();
    }

    boolean matches(Container container, Level level);

    default boolean matches(Container container) {
        return matches(container, null);
    }

    boolean canCraftInDimensions(int width, int height);
}
