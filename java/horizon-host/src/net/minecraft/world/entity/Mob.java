package net.minecraft.world.entity;

import net.minecraft.util.Identifier;

/**
 * Minimal compatibility shim for Minecraft's Mob.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class Mob extends LivingEntity {
    private boolean aggressive = false;

    public Mob(Identifier type) {
        super(type);
    }

    public Mob(EntityType<? extends Mob> type) {
        super(type);
    }

    public void aiStep() {
        // Override in subclasses
    }

    public boolean isAggressive() {
        return aggressive;
    }

    public void setAggressive(boolean aggressive) {
        this.aggressive = aggressive;
    }

    @Override
    public void tick() {
        aiStep();
        super.tick();
    }

    @Override
    public String toString() {
        return "Mob[" + (getType() != null ? getType().toString() : "unknown") + ", aggressive=" + aggressive + "]";
    }
}
