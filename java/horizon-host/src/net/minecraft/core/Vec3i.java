package net.minecraft.core;

/**
 * Compatibility shim for Minecraft's Vec3i: an immutable integer vector.
 * The arithmetic here is the real vanilla arithmetic (same expression order),
 * so results match a real 26.x jar bit for bit.
 */
public class Vec3i implements Comparable<Vec3i> {
    public static final Vec3i ZERO = new Vec3i(0, 0, 0);

    private int x;
    private int y;
    private int z;

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Vec3i vec)) {
            return false;
        }
        return this.getX() == vec.getX() && this.getY() == vec.getY() && this.getZ() == vec.getZ();
    }

    @Override
    public int hashCode() {
        return (this.getY() + this.getZ() * 31) * 31 + this.getX();
    }

    @Override
    public int compareTo(Vec3i other) {
        if (this.getY() == other.getY()) {
            return this.getZ() == other.getZ() ? this.getX() - other.getX() : this.getZ() - other.getZ();
        }
        return this.getY() - other.getY();
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    protected Vec3i setX(int x) {
        this.x = x;
        return this;
    }

    protected Vec3i setY(int y) {
        this.y = y;
        return this;
    }

    protected Vec3i setZ(int z) {
        this.z = z;
        return this;
    }

    public Vec3i offset(int dx, int dy, int dz) {
        return new Vec3i(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
    }

    public Vec3i offset(Vec3i other) {
        return this.offset(other.getX(), other.getY(), other.getZ());
    }

    public Vec3i subtract(Vec3i other) {
        return this.offset(-other.getX(), -other.getY(), -other.getZ());
    }

    public Vec3i multiply(int factor) {
        return this.multiply(factor, factor, factor);
    }

    public Vec3i multiply(int x, int y, int z) {
        return new Vec3i(this.getX() * x, this.getY() * y, this.getZ() * z);
    }

    public Vec3i above() {
        return this.above(1);
    }

    public Vec3i above(int distance) {
        return this.relative(Direction.UP, distance);
    }

    public Vec3i below() {
        return this.below(1);
    }

    public Vec3i below(int distance) {
        return this.relative(Direction.DOWN, distance);
    }

    public Vec3i north() {
        return this.north(1);
    }

    public Vec3i north(int distance) {
        return this.relative(Direction.NORTH, distance);
    }

    public Vec3i south() {
        return this.south(1);
    }

    public Vec3i south(int distance) {
        return this.relative(Direction.SOUTH, distance);
    }

    public Vec3i west() {
        return this.west(1);
    }

    public Vec3i west(int distance) {
        return this.relative(Direction.WEST, distance);
    }

    public Vec3i east() {
        return this.east(1);
    }

    public Vec3i east(int distance) {
        return this.relative(Direction.EAST, distance);
    }

    public Vec3i relative(Direction direction) {
        return this.relative(direction, 1);
    }

    public Vec3i relative(Direction direction, int distance) {
        return distance == 0
                ? this
                : new Vec3i(
                        this.getX() + direction.getStepX() * distance,
                        this.getY() + direction.getStepY() * distance,
                        this.getZ() + direction.getStepZ() * distance);
    }

    public Vec3i relative(Direction.Axis axis, int distance) {
        if (distance == 0) {
            return this;
        }
        return new Vec3i(
                this.getX() + axis.choose(distance, 0, 0),
                this.getY() + axis.choose(0, distance, 0),
                this.getZ() + axis.choose(0, 0, distance));
    }

    public Vec3i cross(Vec3i other) {
        return new Vec3i(
                this.getY() * other.getZ() - this.getZ() * other.getY(),
                this.getZ() * other.getX() - this.getX() * other.getZ(),
                this.getX() * other.getY() - this.getY() * other.getX());
    }

    public boolean closerThan(Vec3i other, double distance) {
        return this.distSqr(other) < distance * distance;
    }

    public boolean closerToCenterThan(Position position, double distance) {
        return this.distToCenterSqr(position) < distance * distance;
    }

    public double distSqr(Vec3i other) {
        return this.distToLowCornerSqr(other.getX(), other.getY(), other.getZ());
    }

    public double distToCenterSqr(Position position) {
        return this.distToCenterSqr(position.x(), position.y(), position.z());
    }

    public double distToCenterSqr(double x, double y, double z) {
        double dx = (double) this.getX() + 0.5 - x;
        double dy = (double) this.getY() + 0.5 - y;
        double dz = (double) this.getZ() + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distToLowCornerSqr(double x, double y, double z) {
        double dx = (double) this.getX() - x;
        double dy = (double) this.getY() - y;
        double dz = (double) this.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public int distManhattan(Vec3i other) {
        float f = (float) Math.abs(other.getX() - this.getX());
        float g = (float) Math.abs(other.getY() - this.getY());
        float h = (float) Math.abs(other.getZ() - this.getZ());
        return (int) (f + g + h);
    }

    public int distChessboard(Vec3i other) {
        int i = Math.abs(this.getX() - other.getX());
        int j = Math.abs(this.getY() - other.getY());
        int k = Math.abs(this.getZ() - other.getZ());
        return Math.max(Math.max(i, j), k);
    }

    public int get(Direction.Axis axis) {
        return axis.choose(this.getX(), this.getY(), this.getZ());
    }

    @Override
    public String toString() {
        // Vanilla builds this with Guava's MoreObjects.toStringHelper(this), which
        // prefixes the object's simple class name: BlockPos{x=0, y=0, z=0}.
        String simpleName = this.getClass().getSimpleName();
        return simpleName + "{x=" + this.getX() + ", y=" + this.getY() + ", z=" + this.getZ() + "}";
    }

    public String toShortString() {
        return this.getX() + ", " + this.getY() + ", " + this.getZ();
    }
}
