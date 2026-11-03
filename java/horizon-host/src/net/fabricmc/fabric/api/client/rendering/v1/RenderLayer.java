package net.fabricmc.fabric.api.client.rendering.v1;

/**
 * Minimal compatibility shim for Fabric's rendering layer concept.
 * Phase 4 of fabric-runtime: Fabric API implementation.
 */
public enum RenderLayer {
    SOLID,
    CUTOUT,
    CUTOUT_MIPPED,
    TRANSLUCENT,
    TRIPWIRE;

    public static RenderLayer getByName(String name) {
        for (RenderLayer layer : values()) {
            if (layer.name().equalsIgnoreCase(name)) {
                return layer;
            }
        }
        return SOLID;
    }
}
