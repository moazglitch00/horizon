package net.minecraft.world.item.crafting;

/**
 * Compatibility shim for Minecraft's RecipeType.
 * Identifies types of recipes (crafting, smelting, etc.).
 */
public final class RecipeType<T> {
    public static final RecipeType<Object> CRAFTING = new RecipeType<>("crafting");
    public static final RecipeType<Object> SMELTING = new RecipeType<>("smelting");
    public static final RecipeType<Object> BLASTING = new RecipeType<>("blasting");
    public static final RecipeType<Object> SMOKING = new RecipeType<>("smoking");
    public static final RecipeType<Object> CAMPFIRE = new RecipeType<>("campfire");

    private final String name;

    private RecipeType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
