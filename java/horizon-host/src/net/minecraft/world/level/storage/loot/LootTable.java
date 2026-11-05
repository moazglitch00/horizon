package net.minecraft.world.level.storage.loot;

import net.minecraft.resources.Identifier;

/**
 * Minimal compatibility shim for Minecraft's LootTable.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class LootTable {
    public static final LootTable EMPTY = new LootTable(null);

    private final Identifier id;

    public LootTable(Identifier id) {
        this.id = id;
    }

    public Identifier getId() {
        return id;
    }

    @Override
    public String toString() {
        return "LootTable[" + (id != null ? id.toString() : "empty") + "]";
    }
}
