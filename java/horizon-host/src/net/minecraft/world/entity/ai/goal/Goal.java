package net.minecraft.world.entity.ai.goal;

/**
 * Minimal compatibility shim for Minecraft's Goal.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public abstract class Goal {
    public abstract boolean canUse();

    public boolean canContinueToUse() {
        return canUse();
    }

    public void start() {
        // Override in subclasses
    }

    public void stop() {
        // Override in subclasses
    }

    public void tick() {
        // Override in subclasses
    }

    public boolean requiresUpdateEveryTick() {
        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "Goal";
    }
}
