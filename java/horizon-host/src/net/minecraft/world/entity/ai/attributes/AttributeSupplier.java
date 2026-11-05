package net.minecraft.world.entity.ai.attributes;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal compatibility shim for Minecraft's AttributeSupplier.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class AttributeSupplier {
    private final Map<Object, Object> instances;

    public AttributeSupplier() {
        this.instances = new LinkedHashMap<>();
    }

    public AttributeSupplier(Map<Object, Object> instances) {
        this.instances = instances != null ? new LinkedHashMap<>(instances) : new LinkedHashMap<>();
    }

    public Map<Object, Object> getInstances() {
        return instances;
    }

    public boolean hasAttribute(Object attribute) {
        return instances.containsKey(attribute);
    }

    public Object getInstance(Object attribute) {
        return instances.get(attribute);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "AttributeSupplier[" + instances.size() + " attributes]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttributeSupplier other)) return false;
        return Objects.equals(instances, other.instances);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instances);
    }

    public static class Builder {
        private final Map<Object, Object> instances = new LinkedHashMap<>();

        public Builder add(Object attribute, double baseValue) {
            instances.put(attribute, baseValue);
            return this;
        }

        public Builder add(Identifier attribute, double baseValue) {
            instances.put(attribute, baseValue);
            return this;
        }

        public Builder add(String attribute, double baseValue) {
            instances.put(attribute, baseValue);
            return this;
        }

        public AttributeSupplier build() {
            return new AttributeSupplier(instances);
        }

        public AttributeSupplier create() {
            return build();
        }
    }
}
