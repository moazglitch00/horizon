package net.minecraft.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal compatibility shim for Minecraft's Registry.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public final class Registry<T> {
    private final String registryName;
    private final Map<String, T> entries = new HashMap<>();

    private Registry(String registryName) {
        this.registryName = registryName;
    }

    public static <T> Registry<T> create(String registryName) {
        return new Registry<>(registryName);
    }

    public void register(String id, T value) {
        entries.put(id, value);
    }

    public T get(String id) {
        return entries.get(id);
    }

    public boolean containsKey(String id) {
        return entries.containsKey(id);
    }

    public int size() {
        return entries.size();
    }

    public String getRegistryName() {
        return registryName;
    }

    @Override
    public String toString() {
        return "Registry[" + registryName + ", " + entries.size() + " entries]";
    }
}
