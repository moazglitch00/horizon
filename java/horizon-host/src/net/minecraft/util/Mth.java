package net.minecraft.util;

import java.util.UUID;
import java.util.function.IntPredicate;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Compatibility shim for Minecraft's Mth: the arithmetic helpers vanilla code
 * calls instead of {@link Math}.
 *
 * <p>This is not a convenience layer: several methods here are <em>not</em>
 * {@code Math} wrappers in vanilla. {@link #sin(double)} and
 * {@link #cos(double)} read a 65536-entry lookup table, {@link #atan2} uses a
 * 257-entry asin table plus a cubic correction, and {@link #fastInvSqrt} is the
 * Quake-style bit trick. Every body below was transcribed from the bytecode of
 * a real 26.x jar and is checked against it by
 * {@code tools/fabric_shim_parity.py} (same inputs, bit-exact output).
 *
 * <p>Not ported here because they need shims this layer does not have yet, and
 * a stub would be a lie: {@code rotationAroundAxis} (joml),
 * {@code mulAndTruncate} (commons-lang3), {@code outFromOrigin} (streams),
 * and the joml axis constants {@code X_AXIS/Y_AXIS/Z_AXIS}.
 */
public class Mth {
    public static final float PI = 3.1415927F;
    public static final float HALF_PI = 1.5707964F;
    public static final float TWO_PI = 6.2831855F;
    public static final float DEG_TO_RAD = 0.017453292F;
    public static final float RAD_TO_DEG = 57.295776F;
    public static final float EPSILON = 1.0E-5F;
    public static final float SQRT_OF_TWO;

    private static final int SIN_QUANTIZATION = 65536;
    private static final int SIN_MASK = 65535;
    private static final int COS_OFFSET = 16384;
    private static final double SIN_SCALE = 10430.378350470453;
    private static final float[] SIN = new float[65536];
    private static final int[] MULTIPLY_DE_BRUIJN_BIT_POSITION = {
        0, 1, 28, 2, 29, 14, 24, 3, 30, 22, 20, 15, 25, 17, 4, 8,
        31, 27, 13, 23, 21, 19, 16, 7, 26, 12, 18, 6, 11, 5, 10, 9
    };
    private static final double ONE_SIXTH = 0.16666666666666666;
    private static final int FRAC_EXP = 8;
    private static final int LUT_SIZE = 257;
    private static final double FRAC_BIAS;
    private static final double[] ASIN_TAB = new double[257];
    private static final double[] COS_TAB = new double[257];

    static {
        for (int i = 0; i < SIN.length; i++) {
            SIN[i] = (float) Math.sin((double) i / SIN_SCALE);
        }
        FRAC_BIAS = Double.longBitsToDouble(4805340802404319232L);
        for (int i = 0; i < LUT_SIZE; i++) {
            double d = (double) i / 256.0;
            double asin = Math.asin(d);
            COS_TAB[i] = Math.cos(asin);
            ASIN_TAB[i] = asin;
        }
        SQRT_OF_TWO = sqrt(2.0F);
    }

    public Mth() {
    }

    public static float sin(double value) {
        return SIN[(int) ((long) (value * SIN_SCALE) & SIN_MASK)];
    }

    public static float cos(double value) {
        return SIN[(int) ((long) (value * SIN_SCALE + COS_OFFSET) & SIN_MASK)];
    }

    public static float sqrt(float value) {
        return (float) Math.sqrt((double) value);
    }

    public static int floor(float value) {
        return (int) Math.floor((double) value);
    }

    public static int floor(double value) {
        return (int) Math.floor(value);
    }

    public static long lfloor(double value) {
        return (long) Math.floor(value);
    }

    public static float abs(float value) {
        return Math.abs(value);
    }

    public static int abs(int value) {
        return Math.abs(value);
    }

    public static int ceil(float value) {
        return (int) Math.ceil((double) value);
    }

    public static int ceil(double value) {
        return (int) Math.ceil(value);
    }

    public static long ceilLong(double value) {
        return (long) Math.ceil(value);
    }

    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    public static long clamp(long value, long min, long max) {
        return Math.min(Math.max(value, min), max);
    }

    /** Vanilla compares {@code value < min} first, so NaN falls through to min. */
    public static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    /** Same shape as the float overload: {@code value < min}, then {@code min(value, max)}. */
    public static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    public static double clampedLerp(double delta, double start, double end) {
        if (delta < 0.0) {
            return start;
        }
        return delta > 1.0 ? end : lerp(delta, start, end);
    }

    public static float clampedLerp(float delta, float start, float end) {
        if (delta < 0.0F) {
            return start;
        }
        return delta > 1.0F ? end : lerp(delta, start, end);
    }

    public static int absMax(int a, int b) {
        return Math.max(Math.abs(a), Math.abs(b));
    }

    public static float absMax(float a, float b) {
        return Math.max(Math.abs(a), Math.abs(b));
    }

    public static double absMax(double a, double b) {
        return Math.max(Math.abs(a), Math.abs(b));
    }

    public static int chessboardDistance(int x1, int z1, int x2, int z2) {
        return absMax(x2 - x1, z2 - z1);
    }

    public static int floorDiv(int a, int b) {
        return Math.floorDiv(a, b);
    }

    public static boolean equal(float a, float b) {
        return Math.abs(b - a) < 1.0E-5F;
    }

    public static boolean equal(double a, double b) {
        return Math.abs(b - a) < 9.999999747378752E-6;
    }

    public static int positiveModulo(int value, int divisor) {
        return Math.floorMod(value, divisor);
    }

    public static float positiveModulo(float value, float divisor) {
        return (value % divisor + divisor) % divisor;
    }

    public static double positiveModulo(double value, double divisor) {
        return (value % divisor + divisor) % divisor;
    }

    public static boolean isMultipleOf(int value, int divisor) {
        return value % divisor == 0;
    }

    public static byte packDegrees(float degrees) {
        return (byte) floor(degrees * 256.0F / 360.0F);
    }

    public static float unpackDegrees(byte packed) {
        return (float) (packed * 360) / 256.0F;
    }

    public static int wrapDegrees(int degrees) {
        int i = degrees % 360;
        if (i >= 180) {
            i -= 360;
        }
        if (i < -180) {
            i += 360;
        }
        return i;
    }

    public static float wrapDegrees(long degrees) {
        float f = (float) (degrees % 360L);
        if (f >= 180.0F) {
            f -= 360.0F;
        }
        if (f < -180.0F) {
            f += 360.0F;
        }
        return f;
    }

    public static float wrapDegrees(float degrees) {
        float f = degrees % 360.0F;
        if (f >= 180.0F) {
            f -= 360.0F;
        }
        if (f < -180.0F) {
            f += 360.0F;
        }
        return f;
    }

    public static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) {
            d -= 360.0;
        }
        if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }

    public static float degreesDifference(float from, float to) {
        return wrapDegrees(to - from);
    }

    public static float degreesDifferenceAbs(float from, float to) {
        return abs(degreesDifference(from, to));
    }

    public static float rotateIfNecessary(float current, float target, float maxStep) {
        float f = degreesDifference(current, target);
        float g = clamp(f, -maxStep, maxStep);
        return target - g;
    }

    public static float approach(float current, float target, float maxStep) {
        maxStep = abs(maxStep);
        if (current < target) {
            return clamp(current + maxStep, current, target);
        }
        return clamp(current - maxStep, target, current);
    }

    public static float approachDegrees(float current, float target, float maxStep) {
        float f = degreesDifference(current, target);
        return approach(current, current + f, maxStep);
    }

    /**
     * commons-lang3's {@code NumberUtils.toInt(String, int)}: the default when
     * the string is null or not a decimal integer.
     */
    public static int getInt(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static int smallestEncompassingPowerOfTwo(int value) {
        int i = value - 1;
        i |= i >> 1;
        i |= i >> 2;
        i |= i >> 4;
        i |= i >> 8;
        i |= i >> 16;
        return i + 1;
    }

    public static int smallestSquareSide(int itemCount) {
        if (itemCount < 0) {
            throw new IllegalArgumentException("itemCount must be greater than or equal to zero");
        }
        return ceil(Math.sqrt((double) itemCount));
    }

    public static boolean isPowerOfTwo(int value) {
        return value != 0 && (value & value - 1) == 0;
    }

    public static int ceillog2(int value) {
        value = isPowerOfTwo(value) ? value : smallestEncompassingPowerOfTwo(value);
        return MULTIPLY_DE_BRUIJN_BIT_POSITION[(int) ((long) value * 125613361L >> 27) & 31];
    }

    public static int log2(int value) {
        return ceillog2(value) - (isPowerOfTwo(value) ? 0 : 1);
    }

    public static float frac(float value) {
        return value - (float) floor(value);
    }

    public static double frac(double value) {
        return value - (double) lfloor(value);
    }

    public static long getSeed(Vec3i pos) {
        return getSeed(pos.getX(), pos.getY(), pos.getZ());
    }

    public static long getSeed(int x, int y, int z) {
        long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        l = l * l * 42317861L + l * 11L;
        return l >> 16;
    }

    public static double inverseLerp(double value, double start, double end) {
        return (value - start) / (end - start);
    }

    public static float inverseLerp(float value, float start, float end) {
        return (value - start) / (end - start);
    }

    /** Vanilla's table-based atan2, not {@link Math#atan2}. */
    public static double atan2(double y, double x) {
        double d = x * x + y * y;
        if (Double.isNaN(d)) {
            return Double.NaN;
        }
        boolean negY = y < 0.0;
        if (negY) {
            y = -y;
        }
        boolean negX = x < 0.0;
        if (negX) {
            x = -x;
        }
        boolean swapped = y > x;
        if (swapped) {
            double swap = x;
            x = y;
            y = swap;
        }
        double inv = fastInvSqrt(d);
        x *= inv;
        y *= inv;
        double biased = FRAC_BIAS + y;
        int index = (int) Double.doubleToRawLongBits(biased);
        double asin = ASIN_TAB[index];
        double cos = COS_TAB[index];
        double frac = biased - FRAC_BIAS;
        double diff = y * cos - x * frac;
        double corr = diff * (6.0 + diff * diff) * ONE_SIXTH;
        double result = asin + corr;
        if (swapped) {
            result = (Math.PI / 2) - result;
        }
        if (negX) {
            result = Math.PI - result;
        }
        if (negY) {
            result = -result;
        }
        return result;
    }

    public static float invSqrt(float value) {
        return 1.0F / (float) Math.sqrt((double) value);
    }

    public static double invSqrt(double value) {
        return 1.0 / Math.sqrt(value);
    }

    public static double fastInvSqrt(double value) {
        double half = 0.5 * value;
        long bits = Double.doubleToRawLongBits(value);
        bits = 6910469410427058090L - (bits >> 1);
        value = Double.longBitsToDouble(bits);
        value *= 1.5 - half * value * value;
        return value;
    }

    public static float fastInvCubeRoot(float value) {
        int bits = Float.floatToIntBits(value);
        bits = 1419967116 - bits / 3;
        float f = Float.intBitsToFloat(bits);
        f = 0.6666667F * f + 1.0F / (3.0F * f * f * value);
        f = 0.6666667F * f + 1.0F / (3.0F * f * f * value);
        return f;
    }

    public static int hsvToRgb(float hue, float saturation, float value) {
        return hsvToArgb(hue, saturation, value, 0);
    }

    public static int hsvToArgb(float hue, float saturation, float value, int alpha) {
        int i = (int) (hue * 6.0F) % 6;
        float f = hue * 6.0F - (float) i;
        float g = value * (1.0F - saturation);
        float h = value * (1.0F - f * saturation);
        float j = value * (1.0F - (1.0F - f) * saturation);
        float k;
        float l;
        float m;
        switch (i) {
            case 0 -> {
                k = value;
                l = j;
                m = g;
            }
            case 1 -> {
                k = h;
                l = value;
                m = g;
            }
            case 2 -> {
                k = g;
                l = value;
                m = j;
            }
            case 3 -> {
                k = g;
                l = h;
                m = value;
            }
            case 4 -> {
                k = j;
                l = g;
                m = value;
            }
            case 5 -> {
                k = value;
                l = g;
                m = h;
            }
            default -> throw new RuntimeException("Something went wrong when converting from HSV to RGB. Input was " + hue + ", " + saturation + ", " + value);
        }
        // net.minecraft.util.ARGB.color(alpha, r, g, b); ARGB is not a shim yet.
        return (alpha & 255) << 24 | (clamp((int) (k * 255.0F), 0, 255) & 255) << 16
                | (clamp((int) (l * 255.0F), 0, 255) & 255) << 8
                | (clamp((int) (m * 255.0F), 0, 255) & 255);
    }

    public static int murmurHash3Mixer(int value) {
        value ^= value >>> 16;
        value *= -2048144789;
        value ^= value >>> 13;
        value *= -1028477387;
        value ^= value >>> 16;
        return value;
    }

    public static int binarySearch(int from, int to, IntPredicate predicate) {
        int i = to - from;
        while (i > 0) {
            int j = i / 2;
            int k = from + j;
            if (predicate.test(k)) {
                i = j;
            } else {
                from = k + 1;
                i = i - (j + 1);
            }
        }
        return from;
    }

    public static int lerpInt(float delta, int start, int end) {
        return start + floor(delta * (float) (end - start));
    }

    public static int lerpDiscrete(float delta, int start, int end) {
        int i = end - start;
        return start + floor(delta * (float) (i - 1)) + (delta > 0.0F ? 1 : 0);
    }

    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    public static Vec3 lerp(double delta, Vec3 start, Vec3 end) {
        return new Vec3(lerp(delta, start.x, end.x), lerp(delta, start.y, end.y), lerp(delta, start.z, end.z));
    }

    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    public static double lerp2(double deltaX, double deltaY, double x0y0, double x1y0, double x0y1, double x1y1) {
        return lerp(deltaY, lerp(deltaX, x0y0, x1y0), lerp(deltaX, x0y1, x1y1));
    }

    public static double lerp3(double deltaX, double deltaY, double deltaZ, double x0y0z0, double x1y0z0, double x0y1z0,
            double x1y1z0, double x0y0z1, double x1y0z1, double x0y1z1, double x1y1z1) {
        return lerp(deltaZ, lerp2(deltaX, deltaY, x0y0z0, x1y0z0, x0y1z0, x1y1z0),
                lerp2(deltaX, deltaY, x0y0z1, x1y0z1, x0y1z1, x1y1z1));
    }

    public static float catmullrom(float delta, float p0, float p1, float p2, float p3) {
        return 0.5F * (2.0F * p1 + (p2 - p0) * delta
                + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * delta * delta
                + (3.0F * p1 - p0 - 3.0F * p2 + p3) * delta * delta * delta);
    }

    public static double smoothstep(double delta) {
        return delta * delta * delta * (delta * (delta * 6.0 - 15.0) + 10.0);
    }

    public static double smoothstepDerivative(double delta) {
        return 30.0 * delta * delta * (delta - 1.0) * (delta - 1.0);
    }

    public static int sign(double value) {
        if (value == 0.0) {
            return 0;
        }
        return value > 0.0 ? 1 : -1;
    }

    public static float rotLerp(float delta, float start, float end) {
        return start + delta * wrapDegrees(end - start);
    }

    public static double rotLerp(double delta, double start, double end) {
        return start + delta * wrapDegrees(end - start);
    }

    public static float rotLerpRad(float delta, float start, float end) {
        float f = end - start;
        while (f < -3.1415927F) {
            f += 6.2831855F;
        }
        while (f >= 3.1415927F) {
            f -= 6.2831855F;
        }
        return start + delta * f;
    }

    public static float triangleWave(float value, float period) {
        return (Math.abs(value % period - period * 0.5F) - period * 0.25F) / (period * 0.25F);
    }

    public static float square(float value) {
        return value * value;
    }

    public static float cube(float value) {
        return value * value * value;
    }

    public static double square(double value) {
        return value * value;
    }

    public static int square(int value) {
        return value * value;
    }

    public static long square(long value) {
        return value * value;
    }

    public static double clampedMap(double value, double fromStart, double fromEnd, double toStart, double toEnd) {
        return clampedLerp(inverseLerp(value, fromStart, fromEnd), toStart, toEnd);
    }

    public static float clampedMap(float value, float fromStart, float fromEnd, float toStart, float toEnd) {
        return clampedLerp(inverseLerp(value, fromStart, fromEnd), toStart, toEnd);
    }

    public static double map(double value, double fromStart, double fromEnd, double toStart, double toEnd) {
        return lerp(inverseLerp(value, fromStart, fromEnd), toStart, toEnd);
    }

    public static float map(float value, float fromStart, float fromEnd, float toStart, float toEnd) {
        return lerp(inverseLerp(value, fromStart, fromEnd), toStart, toEnd);
    }

    public static int roundToward(int value, int divisor) {
        return positiveCeilDiv(value, divisor) * divisor;
    }

    public static int positiveCeilDiv(int a, int b) {
        return -Math.floorDiv(-a, b);
    }

    public static double lengthSquared(double x, double z) {
        return x * x + z * z;
    }

    public static double length(double x, double z) {
        return Math.sqrt(lengthSquared(x, z));
    }

    public static float length(float x, float z) {
        return (float) Math.sqrt(lengthSquared((double) x, (double) z));
    }

    public static double lengthSquared(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

    public static double length(double x, double y, double z) {
        return Math.sqrt(lengthSquared(x, y, z));
    }

    public static float lengthSquared(float x, float y, float z) {
        return x * x + y * y + z * z;
    }

    public static int quantize(double value, int step) {
        return floor(value / (double) step) * step;
    }

    public static int nextInt(RandomSource random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    public static float nextFloat(RandomSource random, float min, float max) {
        return min >= max ? min : random.nextFloat() * (max - min) + min;
    }

    public static double nextDouble(RandomSource random, double min, double max) {
        return min >= max ? min : random.nextDouble() * (max - min) + min;
    }

    public static UUID createInsecureUUID(RandomSource random) {
        long l = random.nextLong() & -61441L | 16384L;
        long m = random.nextLong() & 4611686018427387903L | Long.MIN_VALUE;
        return new UUID(l, m);
    }

    public static double wobble(double value) {
        return value + (RandomSource.createThreadLocalInstance((long) floor(value * 3000.0)).nextDouble() * 2.0 - 1.0)
            * 1.0E-7 / 2.0;
    }

    public static int randomBetweenInclusive(RandomSource random, int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    public static float randomBetween(RandomSource random, float min, float max) {
        return random.nextFloat() * (max - min) + min;
    }

    public static boolean rayIntersectsAABB(Vec3 from, Vec3 direction, AABB box) {
        double centerX = (box.minX + box.maxX) * 0.5;
        double halfX = (box.maxX - box.minX) * 0.5;
        double offsetX = from.x - centerX;
        if (Math.abs(offsetX) > halfX && offsetX * direction.x >= 0.0) {
            return false;
        }
        double centerY = (box.minY + box.maxY) * 0.5;
        double halfY = (box.maxY - box.minY) * 0.5;
        double offsetY = from.y - centerY;
        if (Math.abs(offsetY) > halfY && offsetY * direction.y >= 0.0) {
            return false;
        }
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        double halfZ = (box.maxZ - box.minZ) * 0.5;
        double offsetZ = from.z - centerZ;
        if (Math.abs(offsetZ) > halfZ && offsetZ * direction.z >= 0.0) {
            return false;
        }
        double absX = Math.abs(direction.x);
        double absY = Math.abs(direction.y);
        double absZ = Math.abs(direction.z);

        double crossYZ = direction.y * offsetZ - direction.z * offsetY;
        if (Math.abs(crossYZ) > halfY * absZ + halfZ * absY) {
            return false;
        }
        double crossZX = direction.z * offsetX - direction.x * offsetZ;
        if (Math.abs(crossZX) > halfX * absZ + halfZ * absX) {
            return false;
        }
        double crossXY = direction.x * offsetY - direction.y * offsetX;
        return Math.abs(crossXY) < halfX * absY + halfY * absX;
    }

    public static float normal(RandomSource random, float mean, float deviation) {
        return mean + (float) random.nextGaussian() * deviation;
    }
}
