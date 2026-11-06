package net.minecraft.core;

import net.minecraft.resources.ResourceKey;

/**
 * Minimal compatibility shim for Minecraft's Holder.
 * Placeholder implementation: references are minted by the Registry shim until
 * the real Holder bridge lands. Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class Holder<T> {
    private Holder() {
    }

    public static final class Reference<T> {
        private final T value;
        private final ResourceKey<T> key;

        private Reference(T value, ResourceKey<T> key) {
            this.value = value;
            this.key = key;
        }

        public static <T> Reference<T> createStandAlone(ResourceKey<T> key, T value) {
            return new Reference<>(value, key);
        }

        public T get() {
            return this.value;
        }

        public ResourceKey<T> getKey() {
            return this.key;
        }

        @Override
        public String toString() {
            return "Holder.Reference[" + (key != null ? key.toString() : "unregistered") + "]";
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Reference)) {
                return false;
            }
            Reference otherRef = (Reference) other;
            return this.value == otherRef.value;
        }

        @Override
        public int hashCode() {
            return this.value == null ? 0 : this.value.hashCode();
        }
    }
}
