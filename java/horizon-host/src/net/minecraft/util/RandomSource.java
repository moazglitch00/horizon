package net.minecraft.util;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource;

/**
 * Compatibility shim for Minecraft's RandomSource: the RNG interface every
 * vanilla system draws from.
 *
 * <p>The default methods, the three implementations and the seeding helpers
 * were transcribed from the bytecode of a real 26.x jar. Every value they
 * produce is compared with the real classes by
 * {@code tools/fabric_shim_parity.py}; random draws must happen in vanilla's
 * order, so the sequence matters as much as the algorithm.
 *
 * <p>One deliberate deviation, named here rather than hidden:
 * {@link #createThreadLocalInstance()} seeds from the JDK's
 * {@link ThreadLocalRandom} instead of netty's. Both return a thread-local
 * seed; the value is never observable, so no sequence can depend on it.
 */
public interface RandomSource {
    double GAUSSIAN_SPREAD_FACTOR = 2.297;

    static RandomSource create() {
        return create(RandomSupport.generateUniqueSeed());
    }

    static RandomSource createThreadSafe() {
        return new ThreadSafeLegacyRandomSource(RandomSupport.generateUniqueSeed());
    }

    static RandomSource create(long seed) {
        return new LegacyRandomSource(seed);
    }

    static RandomSource createThreadLocalInstance() {
        return new SingleThreadedRandomSource(ThreadLocalRandom.current().nextLong());
    }

    static RandomSource createThreadLocalInstance(long seed) {
        return new SingleThreadedRandomSource(seed);
    }

    RandomSource fork();

    PositionalRandomFactory forkPositional();

    void setSeed(long seed);

    int nextInt();

    int nextInt(int bound);

    long nextLong();

    boolean nextBoolean();

    float nextFloat();

    double nextDouble();

    double nextGaussian();

    default int nextIntBetweenInclusive(int min, int max) {
        return this.nextInt(max - min + 1) + min;
    }

    /**
     * Vanilla multiplies by {@code max} rather than by {@code max - min}; the
     * parity fixture caught that, so it is reproduced rather than corrected.
     */
    default double triangle(double min, double max) {
        return min + max * (this.nextDouble() - this.nextDouble());
    }

    default float triangle(float min, float max) {
        return min + max * (this.nextFloat() - this.nextFloat());
    }

    default void consumeCount(int count) {
        for (int i = 0; i < count; i++) {
            this.nextInt();
        }
    }

    default int nextInt(int origin, int bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException("bound - origin is non positive");
        }
        return origin + this.nextInt(bound - origin);
    }
}
