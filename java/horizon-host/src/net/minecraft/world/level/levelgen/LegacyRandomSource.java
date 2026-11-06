package net.minecraft.world.level.levelgen;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Compatibility shim for Minecraft's LegacyRandomSource: the classic 48-bit LCG
 * used by world seeds, feature placement and anything that must reproduce a
 * historical vanilla sequence.
 *
 * <p>The multiplier, increment, mask and shift are copied from the real
 * bytecode, as is the nested {@link LegacyPositionalRandomFactory}. Vanilla
 * reports concurrent use through {@code ThreadingDetector.makeThreadingException};
 * until that class is ported this shim throws a named
 * {@link IllegalStateException} on the same CAS failure. The sequence itself is
 * unaffected, and the deviation is only reachable by misusing one instance from
 * several threads.
 */
public class LegacyRandomSource implements BitRandomSource {
    private static final int MODULUS_BITS = 48;
    private static final long MODULUS_MASK = 281474976710655L;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;
    private final AtomicLong seed = new AtomicLong();
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

    public LegacyRandomSource(long seed) {
        this.setSeed(seed);
    }

    @Override
    public RandomSource fork() {
        return new LegacyRandomSource(this.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new LegacyPositionalRandomFactory(this.nextLong());
    }

    @Override
    public void setSeed(long seed) {
        if (!this.seed.compareAndSet(this.seed.get(), (seed ^ MULTIPLIER) & MODULUS_MASK)) {
            throw new IllegalStateException("LegacyRandomSource accessed from multiple threads");
        }
        this.gaussianSource.reset();
    }

    @Override
    public int next(int bits) {
        long current = this.seed.get();
        long next = (current * MULTIPLIER + INCREMENT) & MODULUS_MASK;
        if (!this.seed.compareAndSet(current, next)) {
            throw new IllegalStateException("LegacyRandomSource accessed from multiple threads");
        }
        return (int) (next >> MODULUS_BITS - bits);
    }

    @Override
    public double nextGaussian() {
        return this.gaussianSource.nextGaussian();
    }

    public static class LegacyPositionalRandomFactory implements PositionalRandomFactory {
        private final long seed;

        public LegacyPositionalRandomFactory(long seed) {
            this.seed = seed;
        }

        @Override
        public RandomSource at(int x, int y, int z) {
            long seed = Mth.getSeed(x, y, z);
            return new LegacyRandomSource(seed ^ this.seed);
        }

        @Override
        public RandomSource fromHashOf(String name) {
            int hash = name.hashCode();
            return new LegacyRandomSource((long) hash ^ this.seed);
        }

        @Override
        public RandomSource fromSeed(long seed) {
            return new LegacyRandomSource(seed);
        }

        @Override
        public void parityConfigString(StringBuilder builder) {
            builder.append("LegacyPositionalRandomFactory{").append(this.seed).append("}");
        }
    }
}
