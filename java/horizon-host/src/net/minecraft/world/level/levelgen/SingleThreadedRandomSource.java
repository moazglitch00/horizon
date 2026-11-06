package net.minecraft.world.level.levelgen;

import net.minecraft.util.RandomSource;

/**
 * Compatibility shim for Minecraft's SingleThreadedRandomSource: the same LCG
 * as {@link LegacyRandomSource} without the atomic seed, for a source that is
 * only ever touched by one thread.
 *
 * <p>Vanilla also exposes this through {@code Mth.wobble}, which seeds it from
 * the value it is wobbling, so the sequence is reproducible and compared with
 * the real jar by {@code tools/fabric_shim_parity.py}.
 */
public class SingleThreadedRandomSource implements BitRandomSource {
    private static final int MODULUS_BITS = 48;
    private static final long MODULUS_MASK = 281474976710655L;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;
    private long seed;
    private MarsagliaPolarGaussian gaussianSource;

    public SingleThreadedRandomSource(long seed) {
        this.setSeed(seed);
    }

    @Override
    public RandomSource fork() {
        return new SingleThreadedRandomSource(this.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new LegacyRandomSource.LegacyPositionalRandomFactory(this.nextLong());
    }

    @Override
    public void setSeed(long seed) {
        this.seed = (seed ^ MULTIPLIER) & MODULUS_MASK;
        if (this.gaussianSource != null) {
            this.gaussianSource.reset();
        }
    }

    @Override
    public int next(int bits) {
        this.seed = (this.seed * MULTIPLIER + INCREMENT) & MODULUS_MASK;
        return (int) (this.seed >> MODULUS_BITS - bits);
    }

    @Override
    public double nextGaussian() {
        if (this.gaussianSource == null) {
            this.gaussianSource = new MarsagliaPolarGaussian(this);
        }
        return this.gaussianSource.nextGaussian();
    }
}
