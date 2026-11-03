package net.minecraft.world.item.crafting;

import net.minecraft.util.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal compatibility shim for Minecraft's RecipeManager.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class RecipeManager {
    public RecipeManager() {
    }

    public RecipeManager(Object registries) {
    }

    public RecipeManager(Object registries, Object featureFlags) {
    }

    public Optional<Recipe> getRecipeFor(Object type, Container container, Level level) {
        return Optional.empty();
    }

    public Optional<Recipe> getRecipeFor(Object type, Object container, Level level) {
        return Optional.empty();
    }

    public Optional<Recipe> getRecipeFor(Object type, Container container, Object level) {
        return Optional.empty();
    }

    public Optional<Recipe> getRecipeFor(Object type, Object container, Object level) {
        return Optional.empty();
    }

    public Map<Identifier, Recipe> byType(Object type) {
        return Map.of();
    }

    public Collection<Recipe> getRecipes() {
        return List.of();
    }

    public void finalizeRecipeLoading(Object featureFlags) {
    }

    @Override
    public String toString() {
        return "RecipeManager[]";
    }
}