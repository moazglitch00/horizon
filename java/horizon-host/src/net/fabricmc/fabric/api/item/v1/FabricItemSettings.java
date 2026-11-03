package net.fabricmc.fabric.api.item.v1;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Minimal compatibility shim for Fabric's Item API.
 * Phase 4 of fabric-runtime: Fabric API implementation.
 */
public interface FabricItemSettings {
    FabricItemSettings maxCount(int count);
    FabricItemSettings maxDamage(int damage);

    static FabricItemSettings create() {
        return new DefaultSettings();
    }

    class DefaultSettings implements FabricItemSettings {
        private int maxCount = 64;
        private int maxDamage = 0;

        @Override
        public FabricItemSettings maxCount(int count) {
            this.maxCount = count;
            return this;
        }

        @Override
        public FabricItemSettings maxDamage(int damage) {
            this.maxDamage = damage;
            return this;
        }

        public int getMaxCount() { return maxCount; }
        public int getMaxDamage() { return maxDamage; }
    }
}
