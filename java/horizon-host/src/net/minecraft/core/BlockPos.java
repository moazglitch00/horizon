package net.minecraft.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * Compatibility shim for Minecraft's BlockPos: an integer block coordinate.
 * Packing, offsets and rotation follow the real vanilla implementation and are
 * checked against a real 26.x jar by the shim parity fixture.
 */
public class BlockPos extends Vec3i {
    public static final int PACKED_HORIZONTAL_LENGTH = 26;
    public static final int PACKED_Y_LENGTH = 12;
    public static final int MAX_HORIZONTAL_COORDINATE = (1 << 25) - 1;

    private static final long PACKED_X_MASK = (1L << PACKED_HORIZONTAL_LENGTH) - 1L;
    private static final long PACKED_Y_MASK = (1L << PACKED_Y_LENGTH) - 1L;
    private static final long PACKED_Z_MASK = (1L << PACKED_HORIZONTAL_LENGTH) - 1L;
    private static final int Y_OFFSET = 0;
    private static final int Z_OFFSET = PACKED_Y_LENGTH;
    private static final int X_OFFSET = PACKED_Y_LENGTH + PACKED_HORIZONTAL_LENGTH;

    public static final BlockPos ZERO = new BlockPos(0, 0, 0);

    public BlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    public BlockPos(Vec3i vec) {
        this(vec.getX(), vec.getY(), vec.getZ());
    }

    public static long offset(long packed, Direction direction) {
        return offset(packed, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    public static long offset(long packed, int dx, int dy, int dz) {
        return asLong(getX(packed) + dx, getY(packed) + dy, getZ(packed) + dz);
    }

    public static int getX(long packed) {
        return (int) (packed << (64 - X_OFFSET - PACKED_HORIZONTAL_LENGTH) >> (64 - PACKED_HORIZONTAL_LENGTH));
    }

    public static int getY(long packed) {
        return (int) (packed << (64 - Y_OFFSET - PACKED_Y_LENGTH) >> (64 - PACKED_Y_LENGTH));
    }

    public static int getZ(long packed) {
        return (int) (packed << (64 - Z_OFFSET - PACKED_HORIZONTAL_LENGTH) >> (64 - PACKED_HORIZONTAL_LENGTH));
    }

    public static BlockPos of(long packed) {
        return new BlockPos(getX(packed), getY(packed), getZ(packed));
    }

    public static BlockPos containing(double x, double y, double z) {
        return new BlockPos(floor(x), floor(y), floor(z));
    }

    public static BlockPos containing(Position position) {
        return containing(position.x(), position.y(), position.z());
    }

    public static BlockPos min(BlockPos a, BlockPos b) {
        return new BlockPos(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    public static BlockPos max(BlockPos a, BlockPos b) {
        return new BlockPos(
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public long asLong() {
        return asLong(this.getX(), this.getY(), this.getZ());
    }

    public static long asLong(int x, int y, int z) {
        long l = 0L;
        l |= ((long) x & PACKED_X_MASK) << X_OFFSET;
        l |= ((long) y & PACKED_Y_MASK) << Y_OFFSET;
        l |= ((long) z & PACKED_Z_MASK) << Z_OFFSET;
        return l;
    }

    public static long getFlatIndex(long packed) {
        return packed & -16L;
    }

    @Override
    public BlockPos offset(int dx, int dy, int dz) {
        return dx == 0 && dy == 0 && dz == 0
                ? this
                : new BlockPos(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
    }

    public Vec3 getCenter() {
        return Vec3.atCenterOf(this);
    }

    public Vec3 getBottomCenter() {
        return Vec3.atBottomCenterOf(this);
    }

    @Override
    public BlockPos offset(Vec3i other) {
        return this.offset(other.getX(), other.getY(), other.getZ());
    }

    @Override
    public BlockPos subtract(Vec3i other) {
        return other.getX() == 0 && other.getY() == 0 && other.getZ() == 0
                ? this
                : this.offset(-other.getX(), -other.getY(), -other.getZ());
    }

    @Override
    public BlockPos multiply(int factor) {
        if (factor == 1) {
            return this;
        }
        return factor == 0 ? ZERO : new BlockPos(this.getX() * factor, this.getY() * factor, this.getZ() * factor);
    }

    @Override
    public BlockPos above() {
        return this.relative(Direction.UP, 1);
    }

    @Override
    public BlockPos above(int distance) {
        return this.relative(Direction.UP, distance);
    }

    @Override
    public BlockPos below() {
        return this.relative(Direction.DOWN, 1);
    }

    @Override
    public BlockPos below(int distance) {
        return this.relative(Direction.DOWN, distance);
    }

    @Override
    public BlockPos north() {
        return this.relative(Direction.NORTH, 1);
    }

    @Override
    public BlockPos north(int distance) {
        return this.relative(Direction.NORTH, distance);
    }

    @Override
    public BlockPos south() {
        return this.relative(Direction.SOUTH, 1);
    }

    @Override
    public BlockPos south(int distance) {
        return this.relative(Direction.SOUTH, distance);
    }

    @Override
    public BlockPos west() {
        return this.relative(Direction.WEST, 1);
    }

    @Override
    public BlockPos west(int distance) {
        return this.relative(Direction.WEST, distance);
    }

    @Override
    public BlockPos east() {
        return this.relative(Direction.EAST, 1);
    }

    @Override
    public BlockPos east(int distance) {
        return this.relative(Direction.EAST, distance);
    }

    @Override
    public BlockPos relative(Direction direction) {
        return this.relative(direction, 1);
    }

    @Override
    public BlockPos relative(Direction direction, int distance) {
        return distance == 0
                ? this
                : new BlockPos(
                        this.getX() + direction.getStepX() * distance,
                        this.getY() + direction.getStepY() * distance,
                        this.getZ() + direction.getStepZ() * distance);
    }

    @Override
    public BlockPos relative(Direction.Axis axis, int distance) {
        if (distance == 0) {
            return this;
        }
        return new BlockPos(
                this.getX() + axis.choose(distance, 0, 0),
                this.getY() + axis.choose(0, distance, 0),
                this.getZ() + axis.choose(0, 0, distance));
    }

    public BlockPos rotate(Rotation rotation) {
        return switch (rotation) {
            case NONE -> this;
            case CLOCKWISE_90 -> new BlockPos(-this.getZ(), this.getY(), this.getX());
            case CLOCKWISE_180 -> new BlockPos(-this.getX(), this.getY(), -this.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(this.getZ(), this.getY(), -this.getX());
        };
    }

    @Override
    public BlockPos cross(Vec3i other) {
        return new BlockPos(
                this.getY() * other.getZ() - this.getZ() * other.getY(),
                this.getZ() * other.getX() - this.getX() * other.getZ(),
                this.getX() * other.getY() - this.getY() * other.getX());
    }

    public BlockPos atY(int y) {
        return new BlockPos(this.getX(), y, this.getZ());
    }

    public BlockPos immutable() {
        return this;
    }

    public MutableBlockPos mutable() {
        return new MutableBlockPos(this.getX(), this.getY(), this.getZ());
    }

    public Vec3 clampLocationWithin(Vec3 location) {
        return new Vec3(
                Mth.clamp(location.x, (double) ((float) this.getX() + 1.0E-5F),
                        (double) this.getX() + 1.0 - 9.999999747378752E-6),
                Mth.clamp(location.y, (double) ((float) this.getY() + 1.0E-5F),
                        (double) this.getY() + 1.0 - 9.999999747378752E-6),
                Mth.clamp(location.z, (double) ((float) this.getZ() + 1.0E-5F),
                        (double) this.getZ() + 1.0 - 9.999999747378752E-6));
    }

    public static Stream<BlockPos> squareOutSouthEast(BlockPos pos) {
        return Stream.of(pos, pos.east(), pos.south(), pos.east().south());
    }

    public static Iterable<BlockPos> withinManhattan(BlockPos center, int xDistance, int yDistance, int zDistance) {
        List<BlockPos> result = new ArrayList<>();
        int limit = xDistance + yDistance + zDistance;
        for (int dx = -xDistance; dx <= xDistance; dx++) {
            for (int dy = -yDistance; dy <= yDistance; dy++) {
                int remaining = limit - Math.abs(dx) - Math.abs(dy);
                if (remaining < 0) {
                    continue;
                }
                int zFrom = Math.max(-zDistance, -remaining);
                int zTo = Math.min(zDistance, remaining);
                for (int dz = zFrom; dz <= zTo; dz++) {
                    result.add(center.offset(dx, dy, dz));
                }
            }
        }
        return result;
    }

    public static Optional<BlockPos> findClosestMatch(
            BlockPos center, int radius, Predicate<BlockPos> predicate) {
        for (BlockPos pos : withinManhattan(center, radius, radius, radius)) {
            if (predicate.test(pos)) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    public static Stream<BlockPos> withinManhattanStream(
            BlockPos center, int xDistance, int yDistance, int zDistance) {
        return StreamSupport.stream(withinManhattan(center, xDistance, yDistance, zDistance).spliterator(), false);
    }

    public static Iterable<BlockPos> betweenClosed(BlockPos first, BlockPos second) {
        return betweenClosed(
                first.getX(), first.getY(), first.getZ(), second.getX(), second.getY(), second.getZ());
    }

    public static Stream<BlockPos> betweenClosedStream(BlockPos first, BlockPos second) {
        return betweenClosedStream(
                first.getX(), first.getY(), first.getZ(), second.getX(), second.getY(), second.getZ());
    }

    public static Stream<BlockPos> betweenClosedStream(int x1, int y1, int z1, int x2, int y2, int z2) {
        return StreamSupport.stream(betweenClosed(x1, y1, z1, x2, y2, z2).spliterator(), false);
    }

    public static Iterable<BlockPos> betweenClosed(int x1, int y1, int z1, int x2, int y2, int z2) {
        int xMin = Math.min(x1, x2);
        int yMin = Math.min(y1, y2);
        int zMin = Math.min(z1, z2);
        int xSize = Math.abs(x1 - x2) + 1;
        int ySize = Math.abs(y1 - y2) + 1;
        int zSize = Math.abs(z1 - z2) + 1;
        List<BlockPos> result = new ArrayList<>(xSize * ySize * zSize);
        for (int dy = 0; dy < ySize; dy++) {
            for (int dz = 0; dz < zSize; dz++) {
                for (int dx = 0; dx < xSize; dx++) {
                    result.add(new BlockPos(xMin + dx, yMin + dy, zMin + dz));
                }
            }
        }
        return result;
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < (double) i ? i - 1 : i;
    }

    /**
     * Compatibility shim for Minecraft's mutable block position. The setters
     * return {@code this} so calls can be chained like vanilla.
     */
    public static class MutableBlockPos extends BlockPos {
        public MutableBlockPos() {
            this(0, 0, 0);
        }

        public MutableBlockPos(int x, int y, int z) {
            super(x, y, z);
        }

        @Override
        public BlockPos immutable() {
            return new BlockPos(this.getX(), this.getY(), this.getZ());
        }

        @Override
        public MutableBlockPos setX(int x) {
            super.setX(x);
            return this;
        }

        @Override
        public MutableBlockPos setY(int y) {
            super.setY(y);
            return this;
        }

        @Override
        public MutableBlockPos setZ(int z) {
            super.setZ(z);
            return this;
        }

        public MutableBlockPos set(int x, int y, int z) {
            this.setX(x);
            this.setY(y);
            this.setZ(z);
            return this;
        }

        public MutableBlockPos set(Vec3i pos) {
            return this.set(pos.getX(), pos.getY(), pos.getZ());
        }

        public MutableBlockPos setWithOffset(Vec3i pos, Direction direction) {
            return this.set(
                    pos.getX() + direction.getStepX(),
                    pos.getY() + direction.getStepY(),
                    pos.getZ() + direction.getStepZ());
        }

        public MutableBlockPos setWithOffset(Vec3i pos, int dx, int dy, int dz) {
            return this.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
        }

        public MutableBlockPos setWithOffset(Vec3i pos, Vec3i offset) {
            return this.setWithOffset(pos, offset.getX(), offset.getY(), offset.getZ());
        }

        public MutableBlockPos move(Direction direction) {
            return this.move(direction, 1);
        }

        public MutableBlockPos move(Direction direction, int distance) {
            return this.move(direction.getStepX() * distance, direction.getStepY() * distance, direction.getStepZ() * distance);
        }

        public MutableBlockPos move(int dx, int dy, int dz) {
            return this.set(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
        }

        public MutableBlockPos move(Vec3i offset) {
            return this.move(offset.getX(), offset.getY(), offset.getZ());
        }

        public MutableBlockPos clamp(Direction.Axis axis, int min, int max) {
            return switch (axis) {
                case X -> this.setX(Math.max(min, Math.min(max, this.getX())));
                case Y -> this.setY(Math.max(min, Math.min(max, this.getY())));
                case Z -> this.setZ(Math.max(min, Math.min(max, this.getZ())));
            };
        }
    }
}
