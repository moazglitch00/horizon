package net.minecraft.world.level.levelgen;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.util.RandomSource;

/**
 * Compatibility shim for Minecraft's ThreadSafeLegacyRandomSource: the legacy
 * LCG with an atomic seed, so it can be shared between threads without a
 * thread-use guard.
 *
 * <p>Unlike {@link LegacyRandomSource#setSeed(long)}, vanilla does not reset the
 * cached Gaussian here, and {@link #next(int)} retries the CAS instead of
 * throwing. Both details are reproduced from the real bytecode.
 */
public class ThreadSafeLegacyRandomSource implements BitRandomSource {
    private static final int MODULUS_BITS = 48;
    private static final long MODULUS_MASK = 281474976710655L;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;
    private final AtomicLong seed = new AtomicLong();
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

    public ThreadSafeLegacyRandomSource(long seed) {
        this.setSeed(seed);
    }

    @Override
    public RandomSource fork() {
        return new ThreadSafeLegacyRandomSource(this.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new LegacyRandomSource.LegacyPositionalRandomFactory(this.nextLong());
    }

    @Override
    public void setSeed(long seed) {
        this.seed.set((seed ^ MULTIPLIER) & MODULUS_MASK);
    }

    @Override
    public int next(int bits) {
        long current;
        long next;
        do {
            current = this.seed.get();
            next = (current * MULTIPLIER + INCREMENT) & MODULUS_MASK;
        } while (!this.seed.compareAndSet(current, next));
        return (int) (next >>> MODULUS_BITS - bits);
    }

    @Override
    public double nextGaussian() {
        return this.gaussianSource.nextGaussian();
    }
}
