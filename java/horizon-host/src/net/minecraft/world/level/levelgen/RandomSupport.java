package net.minecraft.world.level.levelgen;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Compatibility shim for Minecraft's RandomSupport: seed mixing and allocation
 * helpers shared by every RNG.
 *
 * <p>{@link #seedFromHashOf(String)} is the MD5 of the UTF-8 bytes read as two
 * big-endian longs. Vanilla gets that through Guava's
 * {@code Hashing.md5()} / {@code Longs.fromBytes}; the JDK's {@code MD5}
 * produces the same 16 bytes, which keeps this file free of a Guava dependency
 * without changing the result.
 */
public final class RandomSupport {
    public static final long GOLDEN_RATIO_64 = -7046029254386353131L;
    public static final long SILVER_RATIO_64 = 7640891576956012809L;
    private static final AtomicLong SEED_UNIQUIFIER = new AtomicLong(8682522807148012L);

    public RandomSupport() {
    }

    public static long mixStafford13(long value) {
        value = (value ^ value >>> 30) * -4658895280553007687L;
        value = (value ^ value >>> 27) * -7723592293110705685L;
        return value ^ value >>> 31;
    }

    public static Seed128bit upgradeSeedTo128bitUnmixed(long seed) {
        long lo = seed ^ SILVER_RATIO_64;
        long hi = lo + GOLDEN_RATIO_64;
        return new Seed128bit(lo, hi);
    }

    public static Seed128bit upgradeSeedTo128bit(long seed) {
        return upgradeSeedTo128bitUnmixed(seed).mixed();
    }

    public static Seed128bit seedFromHashOf(String name) {
        byte[] hash = md5(name.getBytes(StandardCharsets.UTF_8));
        return new Seed128bit(fromBytes(hash, 0), fromBytes(hash, 8));
    }

    public static long generateUniqueSeed() {
        return SEED_UNIQUIFIER.updateAndGet(value -> value * 1181783497276652981L) ^ System.nanoTime();
    }

    private static byte[] md5(byte[] input) {
        try {
            return MessageDigest.getInstance("MD5").digest(input);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 is required by RandomSupport", exception);
        }
    }

    /** Big-endian, matching Guava's {@code Longs.fromBytes}. */
    private static long fromBytes(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFFL) << 56
            | (bytes[offset + 1] & 0xFFL) << 48
            | (bytes[offset + 2] & 0xFFL) << 40
            | (bytes[offset + 3] & 0xFFL) << 32
            | (bytes[offset + 4] & 0xFFL) << 24
            | (bytes[offset + 5] & 0xFFL) << 16
            | (bytes[offset + 6] & 0xFFL) << 8
            | (bytes[offset + 7] & 0xFFL);
    }

    public record Seed128bit(long seedLo, long seedHi) {
        public Seed128bit xor(long lo, long hi) {
            return new Seed128bit(this.seedLo ^ lo, this.seedHi ^ hi);
        }

        public Seed128bit xor(Seed128bit other) {
            return this.xor(other.seedLo, other.seedHi);
        }

        public Seed128bit mixed() {
            return new Seed128bit(mixStafford13(this.seedLo), mixStafford13(this.seedHi));
        }
    }
}
