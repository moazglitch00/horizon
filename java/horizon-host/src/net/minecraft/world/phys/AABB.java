package net.minecraft.world.phys;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Compatibility shim for Minecraft's AABB: an axis-aligned double box.
 *
 * <p>Bodies follow the real bytecode, including the NaN handling of the
 * {@code dcmpg}/{@code dcmpl} comparisons. Java's own {@code <} and {@code >=}
 * discard NaN comparisons, so the exact comparisons live in two private
 * helpers rather than in each method. {@link #toString()} is deliberately
 * absent: the vanilla format is built by a {@code makeConcatWithConstants}
 * recipe that cannot be verified from this machine, and an unverified format
 * would be a silent mismatch.
 */
public class AABB {
    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public AABB(BlockPos pos) {
        this((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(),
                (double) pos.getX() + 1.0, (double) pos.getY() + 1.0, (double) pos.getZ() + 1.0);
    }

    public AABB(Vec3 min, Vec3 max) {
        this(min.x, min.y, min.z, max.x, max.y, max.z);
    }

    public static AABB unitCubeFromLowerCorner(Vec3 pos) {
        return new AABB(pos.x, pos.y, pos.z, pos.x + 1.0, pos.y + 1.0, pos.z + 1.0);
    }

    public static AABB encapsulatingFullBlocks(BlockPos first, BlockPos second) {
        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());
        return new AABB((double) minX, (double) minY, (double) minZ,
                (double) maxX + 1.0, (double) maxY + 1.0, (double) maxZ + 1.0);
    }

    public AABB setMinX(double x) {
        return new AABB(x, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMinY(double y) {
        return new AABB(this.minX, y, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMinZ(double z) {
        return new AABB(this.minX, this.minY, z, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMaxX(double x) {
        return new AABB(this.minX, this.minY, this.minZ, x, this.maxY, this.maxZ);
    }

    public AABB setMaxY(double y) {
        return new AABB(this.minX, this.minY, this.minZ, this.maxX, y, this.maxZ);
    }

    public AABB setMaxZ(double z) {
        return new AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, z);
    }

    public double min(Direction.Axis axis) {
        return axis.choose(this.minX, this.minY, this.minZ);
    }

    public double max(Direction.Axis axis) {
        return axis.choose(this.maxX, this.maxY, this.maxZ);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AABB)) {
            return false;
        }
        AABB that = (AABB) other;
        if (Double.compare(that.minX, this.minX) != 0) {
            return false;
        }
        if (Double.compare(that.minY, this.minY) != 0) {
            return false;
        }
        if (Double.compare(that.minZ, this.minZ) != 0) {
            return false;
        }
        if (Double.compare(that.maxX, this.maxX) != 0) {
            return false;
        }
        if (Double.compare(that.maxY, this.maxY) != 0) {
            return false;
        }
        return Double.compare(that.maxZ, this.maxZ) == 0;
    }

    @Override
    public int hashCode() {
        int result = hashAxis(this.minX);
        result = 31 * result + hashAxis(this.minY);
        result = 31 * result + hashAxis(this.minZ);
        result = 31 * result + hashAxis(this.maxX);
        result = 31 * result + hashAxis(this.maxY);
        return 31 * result + hashAxis(this.maxZ);
    }

    private static int hashAxis(double value) {
        long bits = Double.doubleToLongBits(value);
        return (int) (bits ^ (bits >>> 32));
    }

    public AABB contract(double dx, double dy, double dz) {
        double x0 = this.minX;
        double y0 = this.minY;
        double z0 = this.minZ;
        double x1 = this.maxX;
        double y1 = this.maxY;
        double z1 = this.maxZ;
        if (dx < 0.0) {
            x0 -= dx;
        } else if (dx > 0.0) {
            x1 -= dx;
        }
        if (dy < 0.0) {
            y0 -= dy;
        } else if (dy > 0.0) {
            y1 -= dy;
        }
        if (dz < 0.0) {
            z0 -= dz;
        } else if (dz > 0.0) {
            z1 -= dz;
        }
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    public AABB expandTowards(Vec3 direction) {
        return this.expandTowards(direction.x, direction.y, direction.z);
    }

    public AABB expandTowards(double dx, double dy, double dz) {
        double x0 = this.minX;
        double y0 = this.minY;
        double z0 = this.minZ;
        double x1 = this.maxX;
        double y1 = this.maxY;
        double z1 = this.maxZ;
        if (dx < 0.0) {
            x0 += dx;
        } else if (dx > 0.0) {
            x1 += dx;
        }
        if (dy < 0.0) {
            y0 += dy;
        } else if (dy > 0.0) {
            y1 += dy;
        }
        if (dz < 0.0) {
            z0 += dz;
        } else if (dz > 0.0) {
            z1 += dz;
        }
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    public AABB inflate(double dx, double dy, double dz) {
        return new AABB(this.minX - dx, this.minY - dy, this.minZ - dz,
                this.maxX + dx, this.maxY + dy, this.maxZ + dz);
    }

    public AABB inflate(double amount) {
        return this.inflate(amount, amount, amount);
    }

    public AABB intersect(AABB other) {
        double x0 = Math.max(this.minX, other.minX);
        double y0 = Math.max(this.minY, other.minY);
        double z0 = Math.max(this.minZ, other.minZ);
        double x1 = Math.min(this.maxX, other.maxX);
        double y1 = Math.min(this.maxY, other.maxY);
        double z1 = Math.min(this.maxZ, other.maxZ);
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    public AABB minmax(AABB other) {
        double x0 = Math.min(this.minX, other.minX);
        double y0 = Math.min(this.minY, other.minY);
        double z0 = Math.min(this.minZ, other.minZ);
        double x1 = Math.max(this.maxX, other.maxX);
        double y1 = Math.max(this.maxY, other.maxY);
        double z1 = Math.max(this.maxZ, other.maxZ);
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    public AABB move(double dx, double dy, double dz) {
        return new AABB(this.minX + dx, this.minY + dy, this.minZ + dz,
                this.maxX + dx, this.maxY + dy, this.maxZ + dz);
    }

    public AABB move(BlockPos pos) {
        return this.move((double) pos.getX(), (double) pos.getY(), (double) pos.getZ());
    }

    public AABB move(Vec3 pos) {
        return this.move(pos.x, pos.y, pos.z);
    }

    private static boolean dcmpGe(double a, double b) {
        return a >= b || Double.isNaN(a) || Double.isNaN(b);
    }

    private static boolean dcmpLe(double a, double b) {
        return a <= b || Double.isNaN(a) || Double.isNaN(b);
    }

    private static boolean dcmpLt(double a, double b) {
        return a < b || Double.isNaN(a) || Double.isNaN(b);
    }

    public boolean intersects(AABB other) {
        return this.intersects(other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ);
    }

    public boolean intersects(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (dcmpGe(this.minX, maxX)) {
            return false;
        }
        if (dcmpLe(this.maxX, minX)) {
            return false;
        }
        if (dcmpGe(this.minY, maxY)) {
            return false;
        }
        if (dcmpLe(this.maxY, minY)) {
            return false;
        }
        if (dcmpGe(this.minZ, maxZ)) {
            return false;
        }
        return !dcmpLe(this.maxZ, minZ);
    }

    public boolean intersects(Vec3 first, Vec3 second) {
        return this.intersects(
                Math.min(first.x, second.x),
                Math.min(first.y, second.y),
                Math.min(first.z, second.z),
                Math.max(first.x, second.x),
                Math.max(first.y, second.y),
                Math.max(first.z, second.z));
    }

    public boolean intersects(BlockPos pos) {
        return this.intersects((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(),
                (double) pos.getX() + 1.0, (double) pos.getY() + 1.0, (double) pos.getZ() + 1.0);
    }

    public boolean contains(Vec3 pos) {
        return this.contains(pos.x, pos.y, pos.z);
    }

    public boolean contains(double x, double y, double z) {
        if (dcmpLt(x, this.minX)) {
            return false;
        }
        if (dcmpGe(x, this.maxX)) {
            return false;
        }
        if (dcmpLt(y, this.minY)) {
            return false;
        }
        if (dcmpGe(y, this.maxY)) {
            return false;
        }
        if (dcmpLt(z, this.minZ)) {
            return false;
        }
        return !dcmpGe(z, this.maxZ);
    }

    public double getSize() {
        return (this.getXsize() + this.getYsize() + this.getZsize()) / 3.0;
    }

    public double getXsize() {
        return this.maxX - this.minX;
    }

    public double getYsize() {
        return this.maxY - this.minY;
    }

    public double getZsize() {
        return this.maxZ - this.minZ;
    }

    public AABB deflate(double dx, double dy, double dz) {
        return this.inflate(-dx, -dy, -dz);
    }

    public AABB deflate(double amount) {
        return this.inflate(-amount);
    }

    public Optional<Vec3> clip(Vec3 from, Vec3 to) {
        return clip(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ, from, to);
    }

    public static Optional<Vec3> clip(double minX, double minY, double minZ, double maxX,
            double maxY, double maxZ, Vec3 from, Vec3 to) {
        double[] best = {1.0};
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        Direction direction = getDirection(minX, minY, minZ, maxX, maxY, maxZ, from,
                best, null, dx, dy, dz);
        if (direction == null) {
            return Optional.empty();
        }
        return Optional.of(from.add(best[0] * dx, best[0] * dy, best[0] * dz));
    }

    public static BlockHitResult clip(Iterable<AABB> boxes, Vec3 from, Vec3 to, BlockPos pos) {
        double[] best = {1.0};
        Direction direction = null;
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        for (AABB box : boxes) {
            direction = getDirection(box.move(pos), from, best, direction, dx, dy, dz);
        }
        if (direction == null) {
            return null;
        }
        return new BlockHitResult(from.add(best[0] * dx, best[0] * dy, best[0] * dz),
                direction, pos, false);
    }

    private static Direction getDirection(AABB box, Vec3 from, double[] best, Direction direction,
            double dx, double dy, double dz) {
        return getDirection(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                from, best, direction, dx, dy, dz);
    }

    private static Direction getDirection(double minX, double minY, double minZ, double maxX,
            double maxY, double maxZ, Vec3 from, double[] best, Direction direction,
            double dx, double dy, double dz) {
        if (dx > 1.0E-7) {
            direction = clipPoint(best, direction, dx, dy, dz, minX, minY, maxY, minZ, maxZ,
                    Direction.WEST, from.x, from.y, from.z);
        } else if (dx < -1.0E-7) {
            direction = clipPoint(best, direction, dx, dy, dz, maxX, minY, maxY, minZ, maxZ,
                    Direction.EAST, from.x, from.y, from.z);
        }
        if (dy > 1.0E-7) {
            direction = clipPoint(best, direction, dy, dz, dx, minY, minZ, maxZ, minX, maxX,
                    Direction.DOWN, from.y, from.z, from.x);
        } else if (dy < -1.0E-7) {
            direction = clipPoint(best, direction, dy, dz, dx, maxY, minZ, maxZ, minX, maxX,
                    Direction.UP, from.y, from.z, from.x);
        }
        if (dz > 1.0E-7) {
            direction = clipPoint(best, direction, dz, dx, dy, minZ, minX, maxX, minY, maxY,
                    Direction.NORTH, from.z, from.x, from.y);
        } else if (dz < -1.0E-7) {
            direction = clipPoint(best, direction, dz, dx, dy, maxZ, minX, maxX, minY, maxY,
                    Direction.SOUTH, from.z, from.x, from.y);
        }
        return direction;
    }

    /**
     * Clips the ray against one slab of the box and, if the hit is strictly
     * closer than the current best, records it. Vanilla ignores hits exactly
     * at {@code best[0]}, so an edge shared by two faces resolves to the
     * first face that was tested.
     */
    private static Direction clipPoint(double[] best, Direction currentDirection,
            double primary, double secondary, double tertiary,
            double primaryMin, double secondaryMin, double secondaryMax,
            double tertiaryMin, double tertiaryMax, Direction face,
            double primaryStart, double secondaryStart, double tertiaryStart) {
        double t = (primaryMin - primaryStart) / primary;
        double second = secondaryStart + t * secondary;
        double third = tertiaryStart + t * tertiary;
        if (t > 0.0 && t < best[0]
                && second > secondaryMin - 1.0E-7 && second < secondaryMax + 1.0E-7
                && third > tertiaryMin - 1.0E-7 && third < tertiaryMax + 1.0E-7) {
            best[0] = t;
            return face;
        }
        return currentDirection;
    }

    public boolean collidedAlongVector(Vec3 motion, List<AABB> boxes) {
        Vec3 center = this.getCenter();
        Vec3 target = center.add(motion);
        for (AABB box : boxes) {
            AABB inflated = box.inflate(
                    this.getXsize() * 0.5 - 1.0E-7,
                    this.getYsize() * 0.5 - 1.0E-7,
                    this.getZsize() * 0.5 - 1.0E-7);
            if (inflated.contains(target) || inflated.contains(center)
                    || inflated.clip(center, target).isPresent()) {
                return true;
            }
        }
        return false;
    }

    public double distanceToSqr(Vec3 target) {
        double x = Math.max(Math.max(this.minX - target.x, target.x - this.maxX), 0.0);
        double y = Math.max(Math.max(this.minY - target.y, target.y - this.maxY), 0.0);
        double z = Math.max(Math.max(this.minZ - target.z, target.z - this.maxZ), 0.0);
        return Mth.lengthSquared(x, y, z);
    }

    public double distanceToSqr(AABB other) {
        double x = Math.max(Math.max(this.minX - other.maxX, other.minX - this.maxX), 0.0);
        double y = Math.max(Math.max(this.minY - other.maxY, other.minY - this.maxY), 0.0);
        double z = Math.max(Math.max(this.minZ - other.maxZ, other.minZ - this.maxZ), 0.0);
        return Mth.lengthSquared(x, y, z);
    }

    public boolean hasNaN() {
        return Double.isNaN(this.minX) || Double.isNaN(this.minY) || Double.isNaN(this.minZ)
                || Double.isNaN(this.maxX) || Double.isNaN(this.maxY) || Double.isNaN(this.maxZ);
    }

    public Vec3 getCenter() {
        return new Vec3(Mth.lerp(0.5, this.minX, this.maxX),
                Mth.lerp(0.5, this.minY, this.maxY),
                Mth.lerp(0.5, this.minZ, this.maxZ));
    }

    public Vec3 getBottomCenter() {
        return new Vec3(Mth.lerp(0.5, this.minX, this.maxX),
                this.minY,
                Mth.lerp(0.5, this.minZ, this.maxZ));
    }

    public Vec3 getMinPosition() {
        return new Vec3(this.minX, this.minY, this.minZ);
    }

    public Vec3 getMaxPosition() {
        return new Vec3(this.maxX, this.maxY, this.maxZ);
    }

    public static AABB ofSize(Vec3 center, double sizeX, double sizeY, double sizeZ) {
        return new AABB(center.x - sizeX / 2.0, center.y - sizeY / 2.0, center.z - sizeZ / 2.0,
                center.x + sizeX / 2.0, center.y + sizeY / 2.0, center.z + sizeZ / 2.0);
    }
}
