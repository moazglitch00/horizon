package net.minecraft.world.level.block.entity;

import java.util.function.Supplier;

/**
 * Minimal compatibility shim for Minecraft's BlockEntityType.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class BlockEntityType<T extends BlockEntity> {
    private final Supplier<T> factory;

    public BlockEntityType(Supplier<T> factory) {
        this.factory = factory;
    }

    public T create() {
        return factory != null ? factory.get() : null;
    }

    public Supplier<T> getFactory() {
        return factory;
    }

    @Override
    public String toString() {
        return "BlockEntityType[" + (factory != null ? factory.toString() : "null") + "]";
    }
}