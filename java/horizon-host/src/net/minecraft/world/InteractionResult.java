package net.minecraft.world;

/**
 * Compatibility shim for Minecraft's InteractionResult.
 * Result of an interaction (clicking, using item, etc.).
 */
public enum InteractionResult {
    PASS(false, false),
    SUCCESS(true, true),
    FAIL(true, false),
    CONSUME(true, true),
    CONSUME_PARTIAL(true, true);

    private final boolean shouldSwing;
    private final boolean shouldCancel;

    InteractionResult(boolean shouldSwing, boolean shouldCancel) {
        this.shouldSwing = shouldSwing;
        this.shouldCancel = shouldCancel;
    }

    public boolean shouldSwing() {
        return shouldSwing;
    }

    public boolean shouldCancel() {
        return shouldCancel;
    }

    public boolean isSuccess() {
        return this == SUCCESS || this == CONSUME || this == CONSUME_PARTIAL;
    }

    @Override
    public String toString() {
        return name();
    }
}
