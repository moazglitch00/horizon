package net.minecraft.world.item;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.TagKey;

/**
 * Compatibility shim for Minecraft's ItemStack.
 * Represents a stack of items in an inventory.
 */
public class ItemStack {
    public static final ItemStack EMPTY = new ItemStack(null);

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

    /** Set the count (mutates and returns this). */
    public ItemStack setCount(int count) {
        this.count = count;
        return this;
    }

    public boolean isEmpty() {
        return item == null || count <= 0;
    }

    public ItemStack copy() {
        return new ItemStack(item, count);
    }

    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (item == null) return InteractionResult.FAIL;
        return item.use(level, player, hand, this);
    }

    public InteractionResult useOn(Level level, Player player, InteractionHand hand, net.minecraft.core.BlockPos pos, net.minecraft.world.phys.BlockHitResult hit) {
        if (item == null) return InteractionResult.FAIL;
        return item.useWithItem(this, level.getBlockState(pos), level, pos, player, hand, hit);
    }

    public Holder<Item> getHolder() {
        return null;
    }

    public boolean is(Item item) {
        return this.item == item;
    }

    public boolean is(Holder<Item> item) {
        return item != null;
    }

    public ItemContainerContents getComponents() {
        return null;
    }

    @Override
    public String toString() {
        return "ItemStack[" + (item != null ? item.toString() : "null") + " x" + count + "]";
    }
}
