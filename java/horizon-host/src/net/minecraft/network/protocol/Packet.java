package net.minecraft.network.protocol;

/**
 * Minimal compatibility shim for Minecraft's Packet.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public interface Packet {
    void write(Object buf);

    default void write(Object buf, Object context) {
        write(buf);
    }

    Object type();

    default Object getType() {
        return type();
    }
}