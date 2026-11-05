package net.minecraft.sounds;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;

/**
 * Minimal compatibility shim for Minecraft's SoundEvent.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class SoundEvent {
    private final Identifier id;
    private final Float range;

    public SoundEvent(Identifier id) {
        this(id, (Float) null);
    }

    public SoundEvent(Identifier id, float range) {
        this(id, Float.valueOf(range));
    }

    public SoundEvent(Identifier id, Float range) {
        this.id = id;
        this.range = range;
    }

    public SoundEvent(Identifier id, Optional<Float> range) {
        this(id, range != null && range.isPresent() ? range.get() : null);
    }

    public static SoundEvent createVariableRangeEvent(Identifier id) {
        return new SoundEvent(id, (Float) null);
    }

    public static SoundEvent createFixedRangeEvent(Identifier id, float range) {
        return new SoundEvent(id, Float.valueOf(range));
    }

    public Identifier getId() {
        return id;
    }

    public Identifier id() {
        return id;
    }

    public Identifier location() {
        return id;
    }

    public Float getRange() {
        return range;
    }

    public Float range() {
        return range;
    }

    public Optional<Float> rangeOptional() {
        return Optional.ofNullable(range);
    }

    @Override
    public String toString() {
        return "SoundEvent[" + (id != null ? id.toString() : "unregistered")
                + (range != null ? ", range=" + range : "") + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SoundEvent other)) return false;
        return Objects.equals(id, other.id) && Objects.equals(range, other.range);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, range);
    }
}