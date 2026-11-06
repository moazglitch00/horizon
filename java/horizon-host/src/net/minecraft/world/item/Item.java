package net.minecraft.world.item;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.TagKey;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Compatibility shim for Minecraft's Item.
 *
 * <p>Item is one of the most important classes for Fabric mods.
 * Item mods need getBlock(), getFoodProperties(), isStackable(), use(), etc.
 */
public class Item {
    private final Properties properties;
    private Identifier registryName;
    private String creatorModId;

    public Item(Properties properties) {
        this.properties = properties;
    }

    public Properties getProperties() {
        return properties;
    }

    /** Get the block this item places (null if not a block item). */
    public Block getBlock() {
        return null;
    }

    /** Get food properties (null if not food). */
    public FoodProperties getFoodProperties() {
        return null;
    }

    /** Check if item stacks. */
    public boolean isStackable() {
        return true;
    }

    /** Check if item is edible. */
    public boolean isEdible() {
        return false;
    }

    /** Check if item is enchantable. */
    public boolean isEnchantable() {
        return false;
    }

    /** Check if item can be damaged. */
    public boolean isDamageable() {
        return false;
    }

    /** Check if piglins barter for it. */
    public boolean isBarterable() {
        return false;
    }

    /** Get use animation type. */
    public UseAnimation getUseAnimation(ItemStack stack) {
        return UseAnimation.NONE;
    }

    /** Get max use duration. */
    public int getMaxUseDuration(ItemStack stack) {
        return 0;
    }

    /** Use item in world. */
    public InteractionResult use(Level level, Player player, InteractionHand hand, ItemStack stack) {
        return InteractionResult.PASS;
    }

    /** Use item (simplified). */
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    /** Use item on a block (e.g., right-click with item in hand). */
    public InteractionResult useWithItem(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    /** Get description/translation id. */
    public String getDescriptionId(ItemStack stack) {
        return "item." + (registryName != null ? registryName.toString() : "unknown");
    }

    /** Get description id (no stack). */
    public String getDescriptionId() {
        return "item." + (registryName != null ? registryName.toString() : "unknown");
    }

    /** Get item rarity. */
    public Rarity getRarity(ItemStack stack) {
        return Rarity.COMMON;
    }

    /** Get item rarity (no stack). */
    public Rarity getRarity() {
        return Rarity.COMMON;
    }

    /** Get max stack size. */
    public int getMaxStackSize() {
        return properties.getMaxStackSize();
    }

    /** Get max damage. */
    public int getMaxDamage() {
        return properties.getMaxDamage();
    }

    /** Get default durability. */
    public int getDefaultDurability() {
        return properties.getMaxDamage();
    }

    /** Get burn time in furnace. */
    public int getBurnTime(ItemStack stack, RecipeType<?> recipeType) {
        return 0;
    }

    /** Get food properties for stack. */
    public FoodProperties getFoodProperties(ItemStack stack) {
        return null;
    }

    /** Check if food. */
    public boolean isFood() {
        return false;
    }

    /** Check if can eat. */
    public boolean canEat(ItemStack stack) {
        return false;
    }

    /** Get use duration remaining. */
    public int getUseDurationRemaining(ItemStack stack) {
        return 0;
    }

    /** Get cooldown. */
    public int getCooldown() {
        return 0;
    }

    /** Check if enabled. */
    public boolean isEnabled() {
        return true;
    }

    /** Check if stackable (for stack). */
    public boolean isStackable(ItemStack stack) {
        return true;
    }

    /** Get display name. */
    public Component getItemStackDisplayName(ItemStack stack) {
        return Component.literal(getDescriptionId(stack));
    }

    /** Get name. */
    public String getName(ItemStack stack) {
        return getDescriptionId(stack);
    }

    /** Get registry name. */
    public Identifier getRegistryName() {
        return registryName;
    }

    /** Set registry name. */
    public Item setRegistryName(Identifier name) {
        this.registryName = name;
        return this;
    }

    /** Get creator mod id. */
    public String getCreatorModId(ItemStack stack) {
        return creatorModId != null ? creatorModId : "unknown";
    }

    /** Check if item is in tag. */
    public boolean isIn(ItemStack stack, TagKey<Item> tag) {
        return false;
    }

    /** Static: check if item matches. */
    public static boolean is(Item item, ItemStack stack) {
        return item == stack.getItem();
    }

    /** Static: check if stack is in tag. */
    public static boolean is(ItemStack stack, TagKey<Item> tag) {
        return false;
    }

    /** Get item container contents. */
    public ItemContainerContents getComponents() {
        return null;
    }

    @Override
    public String toString() {
        return "Item";
    }

    /* ========================================================================
     * Inner classes
     */

    /**
     * Properties for item creation.
     */
    public static class Properties {
        private int maxStackSize = 64;
        private int maxDamage = 0;

        public Properties() {
        }

        public Properties stacksTo(int maxStackSize) {
            this.maxStackSize = maxStackSize;
            return this;
        }

        public Properties durability(int maxDamage) {
            this.maxDamage = maxDamage;
            return this;
        }

        public int getMaxStackSize() {
            return maxStackSize;
        }

        public int getMaxDamage() {
            return maxDamage;
        }
    }

    /**
     * Use animation enum.
     */
    public enum UseAnimation {
        NONE,
        EAT,
        DRINK,
        BLOCK,
        BOW,
        SPEAR,
        CROSSBOW,
        TOSS,
        BRUSH
    }

    /**
     * Rarity enum.
     */
    public enum Rarity {
        COMMON,
        UNCOMMON,
        RARE,
        EPIC
    }
}
