package net.minecraft.world.entity;

import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Minimal compatibility shim for Minecraft's LivingEntity.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class LivingEntity extends Entity {
    private float health;
    private float maxHealth = 20.0f;

    public LivingEntity(Identifier type) {
        super(type);
        this.health = maxHealth;
    }

    public LivingEntity(EntityType<? extends LivingEntity> type) {
        super(type.getId());
        this.health = maxHealth;
    }

    public float getHealth() {
        return health;
    }

    public void setHealth(float health) {
        this.health = health;
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(float maxHealth) {
        this.maxHealth = maxHealth;
        if (health > maxHealth) {
            health = maxHealth;
        }
    }

    public boolean hurt(DamageSource source, float amount) {
        if (amount <= 0.0f) {
            return false;
        }
        health = Math.max(0.0f, health - amount);
        return true;
    }

    public void heal(float amount) {
        if (amount <= 0.0f) {
            return;
        }
        health = Math.min(maxHealth, health + amount);
    }

    public boolean isDeadOrDying() {
        return health <= 0.0f;
    }

    @Override
    public void tick() {
        // Override in subclasses
    }

    @Override
    public String toString() {
        return "LivingEntity[" + (getType() != null ? getType().toString() : "unknown") + ", health=" + health + "/" + maxHealth + "]";
    }
}
