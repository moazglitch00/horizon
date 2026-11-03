package net.fabricmc.fabric.api.object.builder.v1.block;

import net.minecraft.world.level.block.Block;

/**
 * Minimal compatibility shim for Fabric's FabricBlockSettings.
 * Phase 4 of fabric-runtime: Fabric API implementation.
 */
public interface FabricBlockSettings {
    FabricBlockSettings strength(float strength);
    FabricBlockSettings nonOpaque();
    FabricBlockSettings noCollision();

    static FabricBlockSettings create() {
        return new DefaultSettings();
    }

    class DefaultSettings implements FabricBlockSettings {
        private float strength = 1.0f;
        private boolean opaque = true;
        private boolean collision = true;

        @Override
        public FabricBlockSettings strength(float strength) {
            this.strength = strength;
            return this;
        }

        @Override
        public FabricBlockSettings nonOpaque() {
            this.opaque = false;
            return this;
        }

        @Override
        public FabricBlockSettings noCollision() {
            this.collision = false;
            return this;
        }

        public float getStrength() { return strength; }
        public boolean isOpaque() { return opaque; }
        public boolean hasCollision() { return collision; }
    }
}
