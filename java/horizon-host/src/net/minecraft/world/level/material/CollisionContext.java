package net.minecraft.world.level.material;

/**
 * Compatibility shim for Minecraft's CollisionContext.
 * Provides context for collision shape queries.
 */
public class CollisionContext {
    public static final CollisionContext EMPTY = new CollisionContext();

    private CollisionContext() {}

    public static CollisionContext of(net.minecraft.world.entity.Entity entity) {
        return EMPTY;
    }
}
