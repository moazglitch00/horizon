package net.minecraft.resources;

import net.minecraft.core.Holder;

/**
 * Compatibility shim for Minecraft's TagKey.
 * Identifies tags in registries.
 */
public final class TagKey<T> {
    private final RegistryKey<T> registry;
    private final Identifier name;

    public TagKey(RegistryKey<T> registry, Identifier name) {
        this.registry = registry;
        this.name = name;
    }

    public static <T> TagKey<T> create(RegistryKey<T> registry, Identifier name) {
        return new TagKey<>(registry, name);
    }

    public RegistryKey<T> registry() {
        return registry;
    }

    public Identifier name() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TagKey<?> other = (TagKey<?>) obj;
        return registry.equals(other.registry) && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(registry, name);
    }

    @Override
    public String toString() {
        return "TagKey[" + registry + "/" + name + "]";
    }
}
