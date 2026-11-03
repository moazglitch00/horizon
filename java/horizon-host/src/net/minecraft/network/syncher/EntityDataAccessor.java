package net.minecraft.network.syncher;

/**
 * Minimal compatibility shim for Minecraft's EntityDataAccessor (DataTracker key).
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class EntityDataAccessor<T> {
    private final int id;
    private final Object serializer;

    public EntityDataAccessor(int id, Object serializer) {
        this.id = id;
        this.serializer = serializer;
    }

    public int id() {
        return id;
    }

    public int getId() {
        return id;
    }

    public Object serializer() {
        return serializer;
    }

    public Object getSerializer() {
        return serializer;
    }

    @Override
    public String toString() {
        return "EntityDataAccessor[" + id + "]";
    }
}