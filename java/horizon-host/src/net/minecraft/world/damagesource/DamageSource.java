package net.minecraft.world.damagesource;

/**
 * Minimal compatibility shim for Minecraft's DamageSource.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class DamageSource {
    private final String type;
    private final boolean direct;
    private final String scaling;

    public DamageSource(String type, boolean direct, String scaling) {
        this.type = type;
        this.direct = direct;
        this.scaling = scaling;
    }

    public DamageSource(String type, boolean direct) {
        this(type, direct, null);
    }

    public String getType() {
        return type;
    }

    public String type() {
        return type;
    }

    public boolean isDirect() {
        return direct;
    }

    public boolean direct() {
        return direct;
    }

    public String getScaling() {
        return scaling;
    }

    public String scaling() {
        return scaling;
    }

    @Override
    public String toString() {
        return "DamageSource[" + (type != null ? type : "unknown") + ", direct=" + direct
                + ", scaling=" + (scaling != null ? scaling : "null") + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DamageSource other)) return false;
        return java.util.Objects.equals(type, other.type)
                && direct == other.direct
                && java.util.Objects.equals(scaling, other.scaling);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, direct, scaling);
    }
}