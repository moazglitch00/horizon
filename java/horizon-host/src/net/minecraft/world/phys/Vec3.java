package net.minecraft.world.phys;

import java.util.EnumSet;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;

/**
 * Compatibility shim for Minecraft's Vec3: an immutable 3D double vector.
 * Every operation below is the real vanilla expression (same order of
 * arithmetic), verified against a real 26.x jar by the shim parity fixture.
 */
public class Vec3 implements Position {
    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);
    public static final Vec3 X_AXIS = new Vec3(1.0, 0.0, 0.0);
    public static final Vec3 Y_AXIS = new Vec3(0.0, 1.0, 0.0);
    public static final Vec3 Z_AXIS = new Vec3(0.0, 0.0, 1.0);

    public final double x;
    public final double y;
    public final double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(Vec3i vec) {
        this(vec.getX(), vec.getY(), vec.getZ());
    }

    public static Vec3 atLowerCornerOf(Vec3i vec) {
        return new Vec3(vec.getX(), vec.getY(), vec.getZ());
    }

    public static Vec3 atLowerCornerWithOffset(Vec3i vec, double dx, double dy, double dz) {
        return new Vec3(vec.getX() + dx, vec.getY() + dy, vec.getZ() + dz);
    }

    public static Vec3 atCenterOf(Vec3i vec) {
        return atLowerCornerWithOffset(vec, 0.5, 0.5, 0.5);
    }

    public static Vec3 atBottomCenterOf(Vec3i vec) {
        return atLowerCornerWithOffset(vec, 0.5, 0.0, 0.5);
    }

    /**
     * Vanilla's 26.3 spelling: centre on X/Z from the block position, with an
     * explicit Y instead of the block's own.
     */
    public static Vec3 atCenterOfWithY(Vec3i vec, double y) {
        return new Vec3(vec.getX() + 0.5, y, vec.getZ() + 0.5);
    }

    public static Vec3 upFromBottomCenterOf(Vec3i vec, double yOffset) {
        return atLowerCornerWithOffset(vec, 0.5, yOffset, 0.5);
    }

    public Vec3 vectorTo(Vec3 other) {
        return new Vec3(other.x - this.x, other.y - this.y, other.z - this.z);
    }

    public Vec3 normalize() {
        double d = Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        return d < 9.999999747378752E-6 ? ZERO : new Vec3(this.x / d, this.y / d, this.z / d);
    }

    public double dot(Vec3 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    public Vec3 cross(Vec3 other) {
        return new Vec3(
                this.y * other.z - this.z * other.y,
                this.z * other.x - this.x * other.z,
                this.x * other.y - this.y * other.x);
    }

    public Vec3 subtract(Vec3 other) {
        return this.subtract(other.x, other.y, other.z);
    }

    public Vec3 subtract(double value) {
        return this.subtract(value, value, value);
    }

    public Vec3 subtract(double dx, double dy, double dz) {
        return new Vec3(this.x - dx, this.y - dy, this.z - dz);
    }

    public Vec3 add(double value) {
        return this.add(value, value, value);
    }

    public Vec3 add(Vec3 other) {
        return this.add(other.x, other.y, other.z);
    }

    public Vec3 add(double dx, double dy, double dz) {
        return new Vec3(this.x + dx, this.y + dy, this.z + dz);
    }

    public boolean closerThan(Position position, double distance) {
        return this.distanceToSqr(position.x(), position.y(), position.z()) < distance * distance;
    }

    public double distanceTo(Vec3 other) {
        return Math.sqrt(this.distanceToSqr(other));
    }

    public double distanceToSqr(Vec3 other) {
        return this.distanceToSqr(other.x, other.y, other.z);
    }

    public double distanceToSqr(double x, double y, double z) {
        double dx = this.x - x;
        double dy = this.y - y;
        double dz = this.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public boolean closerThan(Vec3 other, double horizontalDistance, double verticalDistance) {
        double dx = other.x - this.x;
        double dy = other.y - this.y;
        double dz = other.z - this.z;
        return Mth.lengthSquared(dx, dz) < Mth.square(horizontalDistance) && Math.abs(dy) < verticalDistance;
    }

    public Vec3 scale(double factor) {
        return new Vec3(this.x * factor, this.y * factor, this.z * factor);
    }

    public Vec3 reverse() {
        return this.scale(-1.0);
    }

    public Vec3 multiply(Vec3 other) {
        return this.multiply(other.x, other.y, other.z);
    }

    public Vec3 multiply(double x, double y, double z) {
        return new Vec3(this.x * x, this.y * y, this.z * z);
    }

    public Vec3 horizontal() {
        return new Vec3(this.x, 0.0, this.z);
    }

    public double length() {
        return Math.sqrt(this.lengthSqr());
    }

    public double lengthSqr() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    public double horizontalDistance() {
        return Math.sqrt(this.horizontalDistanceSqr());
    }

    public double horizontalDistanceSqr() {
        return this.x * this.x + this.z * this.z;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Vec3 vec)) {
            return false;
        }
        return Double.compare(vec.x, this.x) == 0
                && Double.compare(vec.y, this.y) == 0
                && Double.compare(vec.z, this.z) == 0;
    }

    @Override
    public int hashCode() {
        long l = Double.doubleToLongBits(this.x);
        int i = (int) (l ^ l >>> 32);
        l = Double.doubleToLongBits(this.y);
        i = 31 * i + (int) (l ^ l >>> 32);
        l = Double.doubleToLongBits(this.z);
        i = 31 * i + (int) (l ^ l >>> 32);
        return i;
    }

    @Override
    public String toString() {
        return "(" + this.x + ", " + this.y + ", " + this.z + ")";
    }

    public Vec3 lerp(Vec3 other, double delta) {
        return new Vec3(Mth.lerp(delta, this.x, other.x), Mth.lerp(delta, this.y, other.y), Mth.lerp(delta, this.z, other.z));
    }

    /** Vanilla takes radians here (not degrees) and uses the lookup-table sin/cos. */
    public Vec3 xRot(float radians) {
        float f = Mth.cos((double) radians);
        float g = Mth.sin((double) radians);
        double d = this.x;
        double e = this.y * (double) f + this.z * (double) g;
        double h = this.z * (double) f - this.y * (double) g;
        return new Vec3(d, e, h);
    }

    public Vec3 yRot(float radians) {
        float f = Mth.cos((double) radians);
        float g = Mth.sin((double) radians);
        double d = this.x * (double) f + this.z * (double) g;
        double e = this.y;
        double h = this.z * (double) f - this.x * (double) g;
        return new Vec3(d, e, h);
    }

    public Vec3 zRot(float radians) {
        float f = Mth.cos((double) radians);
        float g = Mth.sin((double) radians);
        double d = this.x * (double) f + this.y * (double) g;
        double e = this.y * (double) f - this.x * (double) g;
        double h = this.z;
        return new Vec3(d, e, h);
    }

    public Vec3 rotateClockwise90() {
        return new Vec3(this.z, this.y, -this.x);
    }

    public static Vec3 directionFromRotation(Vec2 rotation) {
        return directionFromRotation(rotation.x, rotation.y);
    }

    public static Vec3 directionFromRotation(float yRot, float xRot) {
        float f = Mth.cos((double) (-xRot * 0.017453292F - 3.1415927F));
        float g = Mth.sin((double) (-xRot * 0.017453292F - 3.1415927F));
        float h = -Mth.cos((double) (-yRot * 0.017453292F));
        float i = Mth.sin((double) (-yRot * 0.017453292F));
        return new Vec3((double) (g * h), (double) i, (double) (f * h));
    }

    public Vec2 rotation() {
        float f = (float) Math.atan2(-this.x, this.z) * 57.295776F;
        float g = (float) Math.asin(-this.y / Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z)) * 57.295776F;
        return new Vec2(g, f);
    }

    public Vec3 align(EnumSet<Direction.Axis> axes) {
        double d = axes.contains(Direction.Axis.X) ? (double) Mth.floor(this.x) : this.x;
        double e = axes.contains(Direction.Axis.Y) ? (double) Mth.floor(this.y) : this.y;
        double f = axes.contains(Direction.Axis.Z) ? (double) Mth.floor(this.z) : this.z;
        return new Vec3(d, e, f);
    }

    public double get(Direction.Axis axis) {
        return axis.choose(this.x, this.y, this.z);
    }

    public Vec3 with(Direction.Axis axis, double value) {
        double d = axis == Direction.Axis.X ? value : this.x;
        double e = axis == Direction.Axis.Y ? value : this.y;
        double f = axis == Direction.Axis.Z ? value : this.z;
        return new Vec3(d, e, f);
    }

    public Vec3 relative(Direction direction, double distance) {
        return new Vec3(
                this.x + (double) direction.getStepX() * distance,
                this.y + (double) direction.getStepY() * distance,
                this.z + (double) direction.getStepZ() * distance);
    }

    @Override
    public final double x() {
        return this.x;
    }

    @Override
    public final double y() {
        return this.y;
    }

    @Override
    public final double z() {
        return this.z;
    }

    public Vec3 projectedOn(Vec3 other) {
        if (other.lengthSqr() == 0.0) {
            return other;
        }
        return other.scale(this.dot(other)).scale(1.0 / other.lengthSqr());
    }

    public boolean isFinite() {
        return Double.isFinite(this.x) && Double.isFinite(this.y) && Double.isFinite(this.z);
    }

    /** Kept for shims written against the older getter names. */
    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }
}
