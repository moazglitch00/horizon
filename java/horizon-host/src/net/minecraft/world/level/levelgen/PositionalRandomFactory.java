package net.minecraft.world.level.levelgen;

import net.minecraft.util.RandomSource;

/**
 * Compatibility shim for Minecraft's PositionalRandomFactory: derives a
 * {@link RandomSource} from a position, a name or a seed.
 *
 * <p>The two defaults are exactly the real interface bodies; the abstract
 * methods are implemented by the source-specific factories.
 */
public interface PositionalRandomFactory {
    default RandomSource at(net.minecraft.core.BlockPos pos) {
        return this.at(pos.getX(), pos.getY(), pos.getZ());
    }

    default RandomSource fromHashOf(net.minecraft.resources.Identifier id) {
        return this.fromHashOf(id.toString());
    }

    RandomSource fromHashOf(String name);

    RandomSource fromSeed(long seed);

    RandomSource at(int x, int y, int z);

    void parityConfigString(StringBuilder builder);
}
