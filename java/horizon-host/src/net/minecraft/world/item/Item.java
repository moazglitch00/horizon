package net.minecraft.world.item;

import net.minecraft.util.Identifier;

/**
 * Minimal compatibility shim for Minecraft's Item.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class Item {
    private final Properties properties;

    public Item(Properties properties) {
        this.properties = properties;
    }

    public Properties getProperties() {
        return properties;
    }

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

    @Override
    public String toString() {
        return "Item";
    }
}
