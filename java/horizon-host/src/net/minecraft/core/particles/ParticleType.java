package net.minecraft.core.particles;

import net.minecraft.util.Identifier;

import java.util.Objects;

/**
 * Minimal compatibility shim for Minecraft's ParticleType.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class ParticleType<T> {
    private final Identifier id;

    public ParticleType(Identifier id) {
        this.id = id;
    }

    public Identifier getId() {
        return id;
    }

    public Identifier id() {
        return id;
    }

    @Override
    public String toString() {
        return "ParticleType[" + (id != null ? id.toString() : "unregistered") + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParticleType<?> other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}