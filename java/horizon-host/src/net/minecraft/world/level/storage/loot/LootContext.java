package net.minecraft.world.level.storage.loot;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Random;

/**
 * Minimal compatibility shim for Minecraft's LootContext.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class LootContext {
    public final Level level;
    public final Vec3 origin;
    public final Random random;

    public LootContext() {
        this(null, null, new Random());
    }

    public LootContext(Level level) {
        this(level, null, new Random());
    }

    public LootContext(Level level, Vec3 origin) {
        this(level, origin, new Random());
    }

    public LootContext(Level level, Vec3 origin, Random random) {
        this.level = level;
        this.origin = origin;
        this.random = (random != null) ? random : new Random();
    }

    public LootContext(Level level, Vec3 origin, Object random) {
        this(level, origin, (random instanceof Random r) ? r : new Random());
    }

    public Level getLevel() {
        return level;
    }

    public Level level() {
        return level;
    }

    public Vec3 getOrigin() {
        return origin;
    }

    public Vec3 origin() {
        return origin;
    }

    public Random getRandom() {
        return random;
    }

    public Random random() {
        return random;
    }

    @Override
    public String toString() {
        return "LootContext[level=" + (level != null ? level : "null")
                + ", origin=" + (origin != null ? origin : "null")
                + ", random=" + (random != null ? random : "null") + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LootContext other)) return false;
        return Objects.equals(level, other.level)
                && Objects.equals(origin, other.origin)
                && Objects.equals(random, other.random);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, origin, random);
    }
}