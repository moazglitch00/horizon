package net.minecraft.world.entity.ai.attributes;

import net.minecraft.resources.Identifier;

/**
 * Minimal compatibility shim for Minecraft's Attribute.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class Attribute {
    private final Identifier id;
    private final double defaultValue;

    public Attribute(Identifier id, double defaultValue) {
        this.id = id;
        this.defaultValue = defaultValue;
    }

    public Identifier getId() {
        return id;
    }

    public double getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String toString() {
        return "Attribute[" + (id != null ? id.toString() : "unregistered") + ", default=" + defaultValue + "]";
    }
}