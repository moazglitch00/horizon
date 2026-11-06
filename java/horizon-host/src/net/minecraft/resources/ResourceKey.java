package net.minecraft.resources;

/**
 * Compatibility shim for Minecraft's ResourceKey.
 * Registry keys identify entries in registries.
 */
public final class ResourceKey<T> {
    private final RegistryKey<T> registry;
    private final Identifier name;

    public ResourceKey(RegistryKey<T> registry, Identifier name) {
        this.registry = registry;
        this.name = name;
    }

    public static <T> ResourceKey<T> create(RegistryKey<T> registry, Identifier name) {
        return new ResourceKey<>(registry, name);
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
        ResourceKey<?> other = (ResourceKey<?>) obj;
        return registry.equals(other.registry) && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(registry, name);
    }

    @Override
    public String toString() {
        return "ResourceKey[" + registry + "/" + name + "]";
    }
}
