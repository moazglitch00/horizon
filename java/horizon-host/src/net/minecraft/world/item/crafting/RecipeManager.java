package net.minecraft.world.item.crafting;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal compatibility shim for Minecraft's RecipeManager.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class RecipeManager {
    private final Map<Identifier, Recipe> recipes = new java.util.LinkedHashMap<>();

    public RecipeManager() {
    }

    public RecipeManager(Object registries) {
    }

    public RecipeManager(Object registries, Object featureFlags) {
    }

    /** Adds a recipe to the in-memory store, as the recipe loader would. */
    public void addRecipe(Identifier id, Recipe recipe) {
        recipes.put(id, recipe);
    }

    public Optional<Recipe> getRecipeFor(Object type, Container container, Level level) {
        for (Recipe recipe : recipes.values()) {
            if (recipe.matches(container, level)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    public Optional<Recipe> getRecipeFor(Object type, Object container, Level level) {
        if (!(container instanceof Container c)) {
            throw new IllegalArgumentException(
                    "getRecipeFor: container is not a Container: "
                            + (container == null ? "null" : container.getClass().getName()));
        }
        return getRecipeFor(type, c, level);
    }

    public Optional<Recipe> getRecipeFor(Object type, Container container, Object level) {
        if (level != null && !(level instanceof Level)) {
            throw new IllegalArgumentException(
                    "getRecipeFor: level is not a Level: " + level.getClass().getName());
        }
        return getRecipeFor(type, container, (Level) level);
    }

    public Optional<Recipe> getRecipeFor(Object type, Object container, Object level) {
        if (level != null && !(level instanceof Level)) {
            throw new IllegalArgumentException(
                    "getRecipeFor: level is not a Level: " + level.getClass().getName());
        }
        return getRecipeFor(type, container, (Level) level);
    }

    public Map<Identifier, Recipe> byType(Object type) {
        if (type == null) {
            return java.util.Collections.unmodifiableMap(recipes);
        }
        throw new UnsupportedOperationException(
                "recipe-by-type: the shim has no recipe-type registry; filter the store directly "
                        + "(fabric-runtime Phase 6)");
    }

    public Collection<Recipe> getRecipes() {
        return java.util.Collections.unmodifiableCollection(recipes.values());
    }

    public void finalizeRecipeLoading(Object featureFlags) {
        // Nothing to finalize: the store holds already-constructed recipes.
    }

    @Override
    public String toString() {
        return "RecipeManager[]";
    }
}