package net.minecraft.resources;

import net.minecraft.util.Identifier;

/**
 * Minimal compatibility shim for Minecraft's ResourceLocation.
 * Alias (wrapper) for Identifier.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public final class ResourceLocation {
    private final Identifier delegate;

    public ResourceLocation(String namespace, String path) {
        this.delegate = Identifier.of(namespace, path);
    }

    public ResourceLocation(String id) {
        this.delegate = Identifier.parse(id);
    }

    public ResourceLocation(Identifier identifier) {
        this.delegate = identifier;
    }

    public static ResourceLocation of(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    public static ResourceLocation parse(String id) {
        return new ResourceLocation(id);
    }

    public Identifier toIdentifier() {
        return delegate;
    }

    public String getNamespace() {
        return delegate.getNamespace();
    }

    public String getPath() {
        return delegate.getPath();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ResourceLocation other) {
            return delegate.equals(other.delegate);
        }
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}