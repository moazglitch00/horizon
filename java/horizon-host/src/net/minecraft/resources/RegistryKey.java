package net.minecraft.resources;

/**
 * Registry key type - identifies which registry a ResourceKey belongs to.
 */
public final class RegistryKey<T> {
    private final Identifier name;

    public RegistryKey(Identifier name) {
        this.name = name;
    }

    public Identifier name() {
        return name;
    }

    @Override
    public String toString() {
        return "RegistryKey[" + name + "]";
    }
}
