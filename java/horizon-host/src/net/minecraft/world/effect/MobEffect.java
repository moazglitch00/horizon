package net.minecraft.world.effect;

import net.minecraft.resources.Identifier;

/**
 * Minimal compatibility shim for Minecraft's MobEffect (StatusEffect).
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class MobEffect {
    private final Identifier id;
    private final int color;
    private final boolean beneficial;

    public MobEffect(Identifier id, int color, boolean beneficial) {
        this.id = id;
        this.color = color;
        this.beneficial = beneficial;
    }

    public Identifier getId() {
        return id;
    }

    public int getColor() {
        return color;
    }

    public boolean isBeneficial() {
        return beneficial;
    }

    @Override
    public String toString() {
        return "MobEffect[" + (id != null ? id.toString() : "unregistered") + ", color=" + color + ", beneficial=" + beneficial + "]";
    }
}
