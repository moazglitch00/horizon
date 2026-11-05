package net.minecraft.world.level.block;

import net.minecraft.resources.Identifier;

/**
 * Minimal compatibility shim for Minecraft's Block.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class Block {
    private Identifier id;
    private final Properties properties;

    public Block(Properties properties) {
        this.id = null;
        this.properties = properties;
    }

    public Identifier getId() {
        return id;
    }

    public void setId(Identifier id) {
        this.id = id;
    }

    public Properties getProperties() {
        return properties;
    }

    public static class Properties {
        private float strength = 1.0f;
        private boolean requiresTool = false;

        public static Properties of() {
            return new Properties();
        }

        public Properties strength(float strength) {
            this.strength = strength;
            return this;
        }

        public Properties requiresCorrectToolForDrops() {
            this.requiresTool = true;
            return this;
        }

        public float getStrength() {
            return strength;
        }

        public boolean requiresTool() {
            return requiresTool;
        }
    }

    @Override
    public String toString() {
        return "Block[" + (id != null ? id.toString() : "unregistered") + "]";
    }
}
