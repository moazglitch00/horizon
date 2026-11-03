package net.minecraft.world.entity.player;

import net.minecraft.world.entity.Entity;
import net.minecraft.util.Identifier;

/**
 * Minimal compatibility shim for Minecraft's PlayerEntity.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class PlayerEntity extends Entity {
    private String name;
    private int experienceLevel = 0;
    private float health = 20.0f;

    public PlayerEntity(Identifier type, String name) {
        super(type);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public int getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(int level) {
        this.experienceLevel = level;
    }

    public float getHealth() {
        return health;
    }

    public void setHealth(float health) {
        this.health = health;
    }

    @Override
    public String toString() {
        return "PlayerEntity[" + name + "]";
    }
}
