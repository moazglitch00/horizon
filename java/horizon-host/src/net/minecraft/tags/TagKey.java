package net.minecraft.tags;

import net.minecraft.resources.ResourceKey;

/**
 * Minimal compatibility shim for Minecraft's TagKey.
 * Placeholder until the tag system lands. Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public final class TagKey<T> implements Comparable<TagKey<T>> {
    private final ResourceKey<T> key;

    private TagKey(ResourceKey<T> key) {
        this.key = key;
    }

    public static <T> TagKey<T> create(ResourceKey<T> key) {
        return new TagKey<>(key);
    }

    public ResourceKey<T> getKey() {
        return this.key;
    }

    @Override
    public int compareTo(TagKey<T> other) {
        return this.key == null ? 0 : this.key.toString().compareTo(other.key.toString());
    }

    @Override
    public String toString() {
        return "TagKey[" + (key != null ? key.toString() : "unregistered") + "]";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TagKey)) {
            return false;
        }
        TagKey otherKey = (TagKey) other;
        return this.key.equals(otherKey.key);
    }

    @Override
    public int hashCode() {
        return this.key == null ? 0 : this.key.hashCode();
    }
}
