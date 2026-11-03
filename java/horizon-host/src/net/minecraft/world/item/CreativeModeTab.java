package net.minecraft.world.item;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Identifier;

/**
 * Minimal compatibility shim for Minecraft's CreativeModeTab (ItemGroup).
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class CreativeModeTab {
    private final Identifier id;
    private final Component displayName;
    private final ItemStack icon;

    public CreativeModeTab(Identifier id, Component displayName, ItemStack icon) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
    }

    public Identifier getId() {
        return id;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public ItemStack getIcon() {
        return icon;
    }

    @Override
    public String toString() {
        return "CreativeModeTab[" + (id != null ? id.toString() : "unregistered") + "]";
    }
}