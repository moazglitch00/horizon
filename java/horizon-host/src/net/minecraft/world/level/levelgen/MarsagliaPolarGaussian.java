package net.minecraft.world.level.levelgen;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Compatibility shim for Minecraft's MarsagliaPolarGaussian: the polar form of
 * the Box-Muller transform, which caches the second normal draw.
 *
 * <p>The loop condition ({@code v >= 1} or {@code v == 0}) is part of vanilla's
 * draw order and is reproduced exactly.
 */
public class MarsagliaPolarGaussian {
    public final RandomSource randomSource;
    private double nextNextGaussian;
    private boolean haveNextNextGaussian;

    public MarsagliaPolarGaussian(RandomSource randomSource) {
        this.randomSource = randomSource;
    }

    public void reset() {
        this.haveNextNextGaussian = false;
    }

    public double nextGaussian() {
        if (this.haveNextNextGaussian) {
            this.haveNextNextGaussian = false;
            return this.nextNextGaussian;
        }
        double d;
        double e;
        double f;
        do {
            d = 2.0 * this.randomSource.nextDouble() - 1.0;
            e = 2.0 * this.randomSource.nextDouble() - 1.0;
            f = Mth.square(d) + Mth.square(e);
        } while (f >= 1.0 || f == 0.0);
        double g = Math.sqrt(-2.0 * Math.log(f) / f);
        this.nextNextGaussian = e * g;
        this.haveNextNextGaussian = true;
        return d * g;
    }
}
