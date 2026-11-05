package net.minecraft.world.entity.player;

import net.minecraft.world.entity.Entity;
import net.minecraft.resources.Identifier;

/**
 * Minimal compatibility shim for Minecraft's Player.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class Player extends Entity {
    private String name;
    private int experienceLevel = 0;
    private float health = 20.0f;

    public Player(Identifier type, String name) {
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
        return "Player[" + name + "]";
    }
}
