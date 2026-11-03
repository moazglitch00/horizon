package net.minecraft.core.particles;

import net.minecraft.util.Identifier;

import java.util.Objects;

/**
 * Minimal compatibility shim for Minecraft's SimpleParticleType.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class SimpleParticleType extends ParticleType<SimpleParticleType> {
    public SimpleParticleType(Identifier id) {
        super(id);
    }

    @Override
    public String toString() {
        return "SimpleParticleType[" + (getId() != null ? getId().toString() : "unregistered") + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimpleParticleType other)) return false;
        return Objects.equals(getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}