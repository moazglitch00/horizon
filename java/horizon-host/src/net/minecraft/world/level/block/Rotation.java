package net.minecraft.world.level.block;

import net.minecraft.core.Direction;

/**
 * Compatibility shim for Minecraft's Rotation: the four quarter turns around
 * the vertical axis. Composition and direction rotation match vanilla.
 */
public enum Rotation {
    NONE(0, "none"),
    CLOCKWISE_90(1, "clockwise_90"),
    CLOCKWISE_180(2, "180"),
    COUNTERCLOCKWISE_90(3, "counterclockwise_90");

    private final int index;
    private final String id;

    Rotation(int index, String id) {
        this.index = index;
        this.id = id;
    }

    public Rotation getRotated(Rotation other) {
        return switch (other) {
            case CLOCKWISE_90 -> switch (this) {
                case NONE -> CLOCKWISE_90;
                case CLOCKWISE_90 -> CLOCKWISE_180;
                case CLOCKWISE_180 -> COUNTERCLOCKWISE_90;
                case COUNTERCLOCKWISE_90 -> NONE;
            };
            case CLOCKWISE_180 -> switch (this) {
                case NONE -> CLOCKWISE_180;
                case CLOCKWISE_90 -> COUNTERCLOCKWISE_90;
                case CLOCKWISE_180 -> NONE;
                case COUNTERCLOCKWISE_90 -> CLOCKWISE_90;
            };
            case COUNTERCLOCKWISE_90 -> switch (this) {
                case NONE -> COUNTERCLOCKWISE_90;
                case CLOCKWISE_90 -> NONE;
                case CLOCKWISE_180 -> CLOCKWISE_90;
                case COUNTERCLOCKWISE_90 -> CLOCKWISE_180;
            };
            default -> this;
        };
    }

    public Direction rotate(Direction direction) {
        if (direction.getAxis() == Direction.Axis.Y) {
            return direction;
        }
        return switch (this) {
            case CLOCKWISE_90 -> direction.getClockWise();
            case CLOCKWISE_180 -> direction.getOpposite();
            case COUNTERCLOCKWISE_90 -> direction.getCounterClockWise();
            default -> direction;
        };
    }

    /**
     * Rotates a value in {@code [0, period)} by this quarter turn. Vanilla
     * expresses CLOCKWISE_90 as {@code (x + period / 4) % period}, and the other
     * turns the same way; the integer division order matters.
     */
    public int rotate(int x, int period) {
        return switch (this) {
            case CLOCKWISE_90 -> (x + period / 4) % period;
            case CLOCKWISE_180 -> (x + period / 2) % period;
            case COUNTERCLOCKWISE_90 -> (x + period * 3 / 4) % period;
            default -> x;
        };
    }

    public String getSerializedName() {
        return this.id;
    }

    private int getIndex() {
        return this.index;
    }
}
