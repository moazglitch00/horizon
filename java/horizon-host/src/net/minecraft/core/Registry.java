package net.minecraft.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/**
 * Minimal compatibility shim for Minecraft's Registry.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public final class Registry<T> implements Iterable<T> {
    private final String registryName;
    private final Map<String, T> entries = new HashMap<>();
    private final Map<Integer, T> byId = new HashMap<>();
    private final Map<T, Integer> ids = new HashMap<>();
    private final Map<ResourceKey<T>, T> byKey = new HashMap<>();
    private final Map<Integer, ResourceKey<T>> keysById = new HashMap<>();
    private int nextId = 0;

    private Registry(String registryName) {
        this.registryName = registryName;
    }

    public static <T> Registry<T> create(String registryName) {
        return new Registry<>(registryName);
    }

    public void register(String id, T value) {
        entries.put(id, value);
        if (value != null && !ids.containsKey(value)) {
            int numericId = nextId++;
            ids.put(value, numericId);
            byId.put(numericId, value);
            ResourceKey<T> key = keyFor(id);
            byKey.put(key, value);
            keysById.put(numericId, key);
        }
    }

    public void register(ResourceKey<T> key, T value) {
        if (key != null) {
            entries.put(key.toString(), value);
        }
        byKey.put(key, value);
        if (value != null && !ids.containsKey(value)) {
            int numericId = nextId++;
            ids.put(value, numericId);
            byId.put(numericId, value);
            keysById.put(numericId, key);
        }
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

    public T byId(int id) {
        return byId.get(id);
    }

    public Set<Entry<String, T>> entrySet() {
        return entries.entrySet();
    }

    public void forEach(Consumer<? super T> consumer) {
        entries.values().forEach(consumer);
    }

    public int getId(T value) {
        return value == null ? -1 : ids.getOrDefault(value, -1);
    }

    public Optional<ResourceKey<T>> getKey(T value) {
        if (value == null) {
            return Optional.empty();
        }
        Integer id = ids.get(value);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(keysById.get(id));
    }

    public Optional<T> getOptional(ResourceKey<T> key) {
        if (key == null) {
            return Optional.empty();
        }
        T value = byKey.get(key);
        return Optional.ofNullable(value);
    }

    public T getOrThrow(ResourceKey<T> key) {
        return getOptional(key).orElseThrow(() -> new IllegalArgumentException("Unknown registry key: " + key));
    }

    public Optional<ResourceKey<T>> getResourceKey(T value) {
        return getKey(value);
    }

    public Stream<TagKey<T>> getTags() {
        return Stream.empty();
    }

    public T getValue(ResourceKey<T> key) {
        return getOptional(key).orElse(null);
    }

    public T getValueOrThrow(ResourceKey<T> key) {
        return getOrThrow(key);
    }

    @Override
    public Iterator<T> iterator() {
        return entries.values().iterator();
    }

    public ResourceKey<T> key() {
        Iterator<Entry<ResourceKey<T>, T>> iterator = byKey.entrySet().iterator();
        if (iterator.hasNext()) {
            return iterator.next().getKey();
        }
        return null;
    }

    public Optional<Holder.Reference<T>> getHolder(ResourceKey<T> key) {
        T value = getValue(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(Holder.Reference.createStandAlone(key, value));
    }

    private ResourceKey<T> keyFor(String id) {
        String namespace = "minecraft";
        if (registryName != null) {
            int separator = registryName.indexOf(':');
            if (separator >= 0) {
                namespace = registryName.substring(0, separator);
            }
        }
        RegistryKey<T> registryKey = new RegistryKey<>(Identifier.fromNamespaceAndPath(namespace, registryName != null ? registryName : "minecraft"));
        return ResourceKey.create(registryKey, Identifier.fromNamespaceAndPath(namespace, id));
    }

    @Override
    public String toString() {
        return "Registry[" + registryName + ", " + entries.size() + " entries]";
    }
}
