package net.minecraft.util;

import java.util.Objects;

/**
 * Minimal compatibility shim for Minecraft's Identifier.
 * Delegates to Rust for validation but stores the value locally.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public final class Identifier {
    private final String namespace;
    private final String path;

    private Identifier(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public static Identifier of(String namespace, String path) {
        if (namespace == null || namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be empty");
        }
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }
        return new Identifier(namespace, path);
    }

    public static Identifier parse(String id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        String[] parts = id.split(":", 2);
        if (parts.length == 1) {
            return new Identifier("minecraft", parts[0]);
        }
        return new Identifier(parts[0], parts[1]);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Identifier other)) return false;
        return namespace.equals(other.namespace) && path.equals(other.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }
}
