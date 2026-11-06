package net.minecraft.world;

/**
 * Compatibility shim for Minecraft's InteractionHand.
 */
public enum InteractionHand {
    MAIN_HAND(0, "main"),
    OFF_HAND(1, "off");

    private final int id;
    private final String name;

    InteractionHand(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public InteractionHand opposite() {
        return this == MAIN_HAND ? OFF_HAND : MAIN_HAND;
    }

    @Override
    public String toString() {
        return name;
    }
}
