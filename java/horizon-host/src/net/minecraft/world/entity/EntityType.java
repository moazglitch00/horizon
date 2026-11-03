package net.minecraft.world.entity;

import net.minecraft.util.Identifier;

import java.util.function.Supplier;

/**
 * Minimal compatibility shim for Minecraft's EntityType.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class EntityType<T extends Entity> {
    private final Identifier id;
    private final Supplier<T> factory;

    public EntityType(Identifier id, Supplier<T> factory) {
        this.id = id;
        this.factory = factory;
    }

    public Identifier getId() {
        return id;
    }

    public T create() {
        return factory != null ? factory.get() : null;
    }

    @Override
    public String toString() {
        return "EntityType[" + (id != null ? id.toString() : "unknown") + "]";
    }
}