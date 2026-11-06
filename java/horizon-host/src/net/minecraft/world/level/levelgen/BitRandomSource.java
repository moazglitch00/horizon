package net.minecraft.world.level.levelgen;

import net.minecraft.util.RandomSource;

/**
 * Compatibility shim for Minecraft's BitRandomSource: the 48-bit LCG
 * primitives shared by the legacy RNGs, plus the default derivations of the
 * wider {@link RandomSource} operations.
 *
 * <p>Bodies follow the real 26.x bytecode exactly, including the rejection loop
 * in {@link #nextInt(int)} that keeps the result uniform.
 */
public interface BitRandomSource extends RandomSource {
    float FLOAT_MULTIPLIER = 5.9604645E-8F;
    double DOUBLE_MULTIPLIER = 1.1102230246251565E-16D;

    int next(int bits);

    @Override
    default int nextInt() {
        return this.next(32);
    }

    @Override
    default int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        if ((bound & bound - 1) == 0) {
            return (int) ((long) bound * (long) this.next(31) >> 31);
        }
        int i;
        int j;
        do {
            i = this.next(31);
            j = i % bound;
        } while (i - j + (bound - 1) < 0);
        return j;
    }

    @Override
    default long nextLong() {
        int i = this.next(32);
        int j = this.next(32);
        long l = (long) i << 32;
        return l + (long) j;
    }

    @Override
    default boolean nextBoolean() {
        return this.next(1) != 0;
    }

    @Override
    default float nextFloat() {
        return (float) this.next(24) * 5.9604645E-8F;
    }

    @Override
    default double nextDouble() {
        int i = this.next(26);
        int j = this.next(27);
        long l = ((long) i << 27) + (long) j;
        return (double) l * 1.1102230246251565E-16D;
    }
}
