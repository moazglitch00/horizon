package net.minecraft.world.item.enchantment;

import net.minecraft.util.Identifier;

/**
 * Minimal compatibility shim for Minecraft's Enchantment.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class Enchantment {
    private final Identifier id;
    private final int maxLevel;

    public Enchantment(Identifier id, int maxLevel) {
        this.id = id;
        this.maxLevel = maxLevel;
    }

    public Identifier getId() {
        return id;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    @Override
    public String toString() {
        return "Enchantment[" + (id != null ? id.toString() : "unregistered") + ", maxLevel=" + maxLevel + "]";
    }
}