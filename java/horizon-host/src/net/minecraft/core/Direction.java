package net.minecraft.core;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Compatibility shim for Minecraft's Direction: the six axis-aligned
 * directions. Data values, opposites, axis ordering and y-rotation conversion
 * follow the real vanilla implementation.
 */
public enum Direction {
    DOWN(0, -1, 0, "down", AxisDirection.NEGATIVE, Axis.Y),
    UP(0, 1, 0, "up", AxisDirection.POSITIVE, Axis.Y),
    NORTH(0, 0, -1, "north", AxisDirection.NEGATIVE, Axis.Z),
    SOUTH(0, 0, 1, "south", AxisDirection.POSITIVE, Axis.Z),
    WEST(-1, 0, 0, "west", AxisDirection.NEGATIVE, Axis.X),
    EAST(1, 0, 0, "east", AxisDirection.POSITIVE, Axis.X);

    /** Directions in y-rotation order: 0 = south, 90 = west, 180 = north, 270 = east. */
    private static final Direction[] BY_Y_ROT = {SOUTH, WEST, NORTH, EAST};

    private static final Direction[] VALUES = values();
    private static final Direction[] BY_3D_DATA = {DOWN, UP, NORTH, SOUTH, WEST, EAST};
    private static final Direction[] BY_2D_DATA = {SOUTH, WEST, NORTH, EAST};

    private final int stepX;
    private final int stepY;
    private final int stepZ;
    private final String displayName;
    private final AxisDirection axisDirection;
    private final Axis axis;
    private final Vec3i normal;
    private final int data2d;

    Direction(
            int stepX,
            int stepY,
            int stepZ,
            String displayName,
            AxisDirection axisDirection,
            Axis axis) {
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
        this.displayName = displayName;
        this.axisDirection = axisDirection;
        this.axis = axis;
        this.normal = new Vec3i(stepX, stepY, stepZ);
        this.data2d = switch (this) {
            case NORTH -> 2;
            case SOUTH -> 0;
            case WEST -> 1;
            case EAST -> 3;
            default -> -1;
        };
    }

    public static Direction byName(String name) {
        for (Direction direction : VALUES) {
            if (direction.displayName.equals(name)) {
                return direction;
            }
        }
        return null;
    }

    public static Direction from3DDataValue(int data) {
        return BY_3D_DATA[Math.abs(data % BY_3D_DATA.length)];
    }

    public static Direction from2DDataValue(int data) {
        return BY_2D_DATA[Math.abs(data % BY_2D_DATA.length)];
    }

    public static Direction fromYRot(double yRot) {
        return BY_Y_ROT[floor(yRot / 90.0 + 0.5) & 3];
    }

    public static Direction get(AxisDirection axisDirection, Axis axis) {
        return switch (axis) {
            case X -> axisDirection == AxisDirection.POSITIVE ? EAST : WEST;
            case Y -> axisDirection == AxisDirection.POSITIVE ? UP : DOWN;
            case Z -> axisDirection == AxisDirection.POSITIVE ? SOUTH : NORTH;
        };
    }

    public static Direction fromAxisAndDirection(Axis axis, AxisDirection axisDirection) {
        return get(axisDirection, axis);
    }

    public static float getYRot(Direction direction) {
        return switch (direction) {
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> -90.0F;
            default -> throw new IllegalStateException("No y-rotation for direction " + direction);
        };
    }

    public static Direction getNearest(int x, int y, int z, Direction fallback) {
        int i = Math.abs(x);
        int j = Math.abs(y);
        int k = Math.abs(z);
        if (i > j && i > k) {
            return x < 0 ? WEST : EAST;
        }
        if (k > i && k > j) {
            return z < 0 ? NORTH : SOUTH;
        }
        if (j > i && j > k) {
            return y < 0 ? DOWN : UP;
        }
        return fallback;
    }

    public static Direction getNearest(Vec3i position, Direction fallback) {
        return getNearest(position.getX(), position.getY(), position.getZ(), fallback);
    }

    public static Direction getApproximateNearest(double x, double y, double z) {
        return getApproximateNearest((float) x, (float) y, (float) z);
    }

    public static Direction getApproximateNearest(float x, float y, float z) {
        Direction direction = NORTH;
        float best = Float.MIN_VALUE;
        for (Direction candidate : VALUES) {
            float dot = x * candidate.getStepX() + y * candidate.getStepY() + z * candidate.getStepZ();
            if (dot > best) {
                best = dot;
                direction = candidate;
            }
        }
        return direction;
    }

    public static Direction getApproximateNearest(Vec3 vec) {
        return getApproximateNearest(vec.x, vec.y, vec.z);
    }

    public int get3DDataValue() {
        return this.ordinal();
    }

    public int get2DDataValue() {
        return this.data2d;
    }

    public AxisDirection getAxisDirection() {
        return this.axisDirection;
    }

    public Direction getOpposite() {
        return VALUES[this.ordinal() ^ 1];
    }

    public Direction getClockWise(Axis axis) {
        return switch (axis) {
            case X -> this == WEST || this == EAST ? this : this.getClockWiseX();
            case Y -> this == UP || this == DOWN ? this : this.getClockWise();
            case Z -> this == NORTH || this == SOUTH ? this : this.getClockWiseZ();
        };
    }

    public Direction getCounterClockWise(Axis axis) {
        return switch (axis) {
            case X -> this == WEST || this == EAST ? this : this.getCounterClockWiseX();
            case Y -> this == UP || this == DOWN ? this : this.getCounterClockWise();
            case Z -> this == NORTH || this == SOUTH ? this : this.getCounterClockWiseZ();
        };
    }

    /** Clockwise around X; north becomes down, south becomes up (vanilla's tables). */
    private Direction getClockWiseX() {
        return switch (this) {
            case DOWN -> SOUTH;
            case UP -> NORTH;
            case NORTH -> DOWN;
            case SOUTH -> UP;
            default -> throw new IllegalStateException("Unable to get X-rotated facing of " + this);
        };
    }

    private Direction getCounterClockWiseX() {
        return switch (this) {
            case DOWN -> NORTH;
            case UP -> SOUTH;
            case NORTH -> UP;
            case SOUTH -> DOWN;
            default -> throw new IllegalStateException("Unable to get X-rotated facing of " + this);
        };
    }

    private Direction getClockWiseZ() {
        return switch (this) {
            case DOWN -> WEST;
            case UP -> EAST;
            case WEST -> UP;
            case EAST -> DOWN;
            default -> throw new IllegalStateException("Unable to get Z-rotated facing of " + this);
        };
    }

    private Direction getCounterClockWiseZ() {
        return switch (this) {
            case DOWN -> EAST;
            case UP -> WEST;
            case WEST -> DOWN;
            case EAST -> UP;
            default -> throw new IllegalStateException("Unable to get Z-rotated facing of " + this);
        };
    }

    public Direction getClockWise() {
        return switch (this) {
            case NORTH -> EAST;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            case EAST -> SOUTH;
            default -> throw new IllegalStateException("Unable to get Y-rotated facing of " + this);
        };
    }

    public Direction getCounterClockWise() {
        return switch (this) {
            case NORTH -> WEST;
            case SOUTH -> EAST;
            case WEST -> SOUTH;
            case EAST -> NORTH;
            default -> throw new IllegalStateException("Unable to get Y-rotated facing of " + this);
        };
    }

    public int getStepX() {
        return this.stepX;
    }

    public int getStepY() {
        return this.stepY;
    }

    public int getStepZ() {
        return this.stepZ;
    }

    public String getName() {
        return this.displayName;
    }

    public String getSerializedName() {
        return this.displayName;
    }

    public Axis getAxis() {
        return this.axis;
    }

    public Vec3i getUnitVec3i() {
        return this.normal;
    }

    public Vec3 getUnitVec3() {
        return new Vec3(this.stepX, this.stepY, this.stepZ);
    }

    /** Vanilla derives this from the 2D data value, so vertical directions land on 270. */
    public float toYRot() {
        return (float) ((this.data2d & 3) * 90);
    }

    /** Vanilla tests the facing vector against the angle, not the y-rotation value. */
    public boolean isFacingAngle(float yRot) {
        float f = yRot * 0.017453292F;
        float g = -Mth.sin((double) f);
        float h = Mth.cos((double) f);
        return (float) this.normal.getX() * g + (float) this.normal.getZ() * h > 0.0F;
    }

    @Override
    public String toString() {
        return this.displayName;
    }

    /** Vanilla's {@code Mth.floor} for a double. */
    private static int floor(double value) {
        int i = (int) value;
        return value < (double) i ? i - 1 : i;
    }

    public enum AxisDirection {
        POSITIVE(1, "Towards positive"),
        NEGATIVE(-1, "Towards negative");

        private final int step;
        private final String name;

        AxisDirection(int step, String name) {
            this.step = step;
            this.name = name;
        }

        public int getStep() {
            return this.step;
        }

        public String getName() {
            return this.name;
        }

        public AxisDirection opposite() {
            return this == POSITIVE ? NEGATIVE : POSITIVE;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    public enum Axis {
        X("x") {
            @Override
            public Direction getPositive() {
                return EAST;
            }

            @Override
            public Direction getNegative() {
                return WEST;
            }

            @Override
            public int choose(int x, int y, int z) {
                return x;
            }

            @Override
            public double choose(double x, double y, double z) {
                return x;
            }

            @Override
            public boolean choose(boolean x, boolean y, boolean z) {
                return x;
            }
        },
        Y("y") {
            @Override
            public Direction getPositive() {
                return UP;
            }

            @Override
            public Direction getNegative() {
                return DOWN;
            }

            @Override
            public int choose(int x, int y, int z) {
                return y;
            }

            @Override
            public double choose(double x, double y, double z) {
                return y;
            }

            @Override
            public boolean choose(boolean x, boolean y, boolean z) {
                return y;
            }
        },
        Z("z") {
            @Override
            public Direction getPositive() {
                return SOUTH;
            }

            @Override
            public Direction getNegative() {
                return NORTH;
            }

            @Override
            public int choose(int x, int y, int z) {
                return z;
            }

            @Override
            public double choose(double x, double y, double z) {
                return z;
            }

            @Override
            public boolean choose(boolean x, boolean y, boolean z) {
                return z;
            }
        };

        public static final Axis[] VALUES = values();

        private final String name;

        Axis(String name) {
            this.name = name;
        }

        public static Axis byName(String name) {
            for (Axis axis : VALUES) {
                if (axis.name.equals(name)) {
                    return axis;
                }
            }
            return null;
        }

        public String getName() {
            return this.name;
        }

        public boolean isVertical() {
            return this == Y;
        }

        public boolean isHorizontal() {
            return this != Y;
        }

        public abstract Direction getPositive();

        public abstract Direction getNegative();

        public Direction[] getDirections() {
            return new Direction[] {this.getPositive(), this.getNegative()};
        }

        public abstract int choose(int x, int y, int z);

        public abstract double choose(double x, double y, double z);

        public abstract boolean choose(boolean x, boolean y, boolean z);

        @Override
        public String toString() {
            return this.name;
        }

        public String getSerializedName() {
            return this.name;
        }
    }
}
