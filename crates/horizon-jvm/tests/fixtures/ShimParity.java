import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Differential parity fixture for the shim layer.
 *
 * <p>The same source is compiled once against a real Minecraft 26.x jar and run
 * twice: once on the real classes, once on the Horizon shims. Every value is
 * printed as raw bits so the two transcripts must match byte for byte. Any
 * difference is a shim that is not vanilla.
 *
 * <p>Run it with {@code python3 tools/fabric_shim_parity.py}. Only members that
 * exist in both the real jar and the shim may appear here: if the shim lacks a
 * method, the shim run fails with {@code NoSuchMethodError}, which is the point.
 */
public final class ShimParity {
    /**
     * The raw process stdout, captured before Bootstrap swaps {@code System.out}
     * for the game's logger. Printing through it keeps both transcripts plain
     * and free of timestamps.
     */
    private static final java.io.PrintStream OUT =
            new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true);

    private static int lines;

    private static void p(String key, long value) {
        OUT.println(key + "=" + value);
        lines++;
    }

    private static void pf(String key, float value) {
        p(key, Float.floatToRawIntBits(value));
    }

    private static void pd(String key, double value) {
        p(key, Double.doubleToRawLongBits(value));
    }

    private static void ps(String key, String value) {
        OUT.println(key + "=" + value);
        lines++;
    }

    /**
     * Records the exception type when vanilla throws, so the shim has to throw
     * at the same places for the same inputs instead of inventing a value.
     */
    private static void safe(String key, java.util.function.Supplier<String> value) {
        try {
            ps(key, value.get());
        } catch (RuntimeException e) {
            ps(key, "throws:" + e.getClass().getSimpleName());
        }
    }

    private static final double[] DOUBLES = {
        -1.0E9, -1.0E18, -1000.5, -720.0, -359.999, -181.0, -180.0, -90.0,
        -6.283185307179586, -3.141592653589793, -1.5707963267948966, -1.0, -0.5, -1.0E-9,
        -0.0, 0.0, 1.0E-9, 0.25, 0.5, 1.0, 1.5707963267948966, 2.0,
        3.141592653589793, 6.283185307179586, 45.0, 89.5, 90.0, 179.5, 180.0, 270.0,
        359.999, 360.0, 720.0, 1000.5, 1.0E9, 1.0E18,
        Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
        Double.MIN_VALUE, Double.MAX_VALUE,
    };

    private static final float[] FLOATS = {
        -1.0E9F, -720.0F, -359.999F, -181.0F, -180.0F, -90.0F, -3.1415927F, -1.5707964F,
        -1.0F, -0.5F, -1.0E-9F, -0.0F, 0.0F, 1.0E-9F, 0.25F, 0.5F, 1.0F, 1.5707964F,
        3.1415927F, 45.0F, 89.5F, 90.0F, 179.5F, 180.0F, 270.0F, 359.999F, 360.0F,
        720.0F, 1000.5F, 1.0E9F,
        Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
        Float.MIN_VALUE, Float.MAX_VALUE,
    };

    private static final float[] DEGREES = {
        -1080.0F, -720.0F, -360.0F, -359.5F, -270.0F, -180.0F, -90.5F, -1.0F, 0.0F,
        1.0F, 30.0F, 45.0F, 89.5F, 90.0F, 135.0F, 179.5F, 180.0F, 181.0F, 270.0F,
        359.5F, 360.0F, 540.0F, 720.0F, 1234.5F,
    };

    private static boolean bootstrapped;
    private static boolean shimRun;

    /**
     * The real ChunkPos static initialiser registers chunk statuses, which needs
     * the game's registries. Under the shims there is no Bootstrap, and loading
     * the real one would link it against shim classes it was not built for, so
     * the tool tells the fixture which run this is.
     */
    private static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        if (shimRun) {
            return;
        }
        try {
            // Bootstrap reads SharedConstants' version, which the launcher sets
            // normally; detect it from the jar's version.json instead.
            Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
            Class.forName("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("bootstrap failed", e);
        }
    }

    public static void main(String[] args) {
        shimRun = args.length > 0 && args[0].equals("shim");
        mthTrig();
        mthScalar();
        mthWrap();
        mthLerp();
        mthBits();
        rng();
        aabb();
        vectorMath();
        blockPos();
        chunkPos();
        identifier();
        directions();
        rotations();
        OUT.println("lines=" + lines);
    }

    private static void mthTrig() {
        for (int i = 0; i < DOUBLES.length; i++) {
            pd("mth.sin." + i, Mth.sin(DOUBLES[i]));
            pd("mth.cos." + i, Mth.cos(DOUBLES[i]));
            pd("mth.atan2a." + i, Mth.atan2(DOUBLES[i], 1.5));
            pd("mth.atan2b." + i, Mth.atan2(-1.25, DOUBLES[i]));
        }
        pd("mth.atan2.zero", Mth.atan2(0.0, 0.0));
        pd("mth.atan2.pi", Mth.atan2(0.0, -1.0));
        pd("mth.atan2.halfpi", Mth.atan2(1.0, 0.0));
        pd("mth.atan2.quarter", Mth.atan2(1.0, 1.0));
        pd("mth.atan2.threequarter", Mth.atan2(1.0, -1.0));
        pd("mth.atan2.far", Mth.atan2(1.0E-12, 1.0E12));
        pd("mth.atan2.nan", Mth.atan2(Double.NaN, 0.0));
        for (int i = 0; i < FLOATS.length; i++) {
            pf("mth.sqrt." + i, Mth.sqrt(FLOATS[i]));
            pf("mth.fastInvSqrt.f." + i, (float) Mth.fastInvSqrt((double) FLOATS[i]));
            pf("mth.fastInvCubeRoot." + i, Mth.fastInvCubeRoot(FLOATS[i]));
            pf("mth.invSqrt.f." + i, Mth.invSqrt(FLOATS[i]));
            pd("mth.invSqrt.d." + i, Mth.invSqrt((double) FLOATS[i]));
        }
    }

    private static void mthScalar() {
        for (int i = 0; i < DOUBLES.length; i++) {
            double d = DOUBLES[i];
            p("mth.floor.d." + i, Mth.floor(d));
            p("mth.lfloor." + i, Mth.lfloor(d));
            p("mth.ceil.d." + i, Mth.ceil(d));
            p("mth.ceilLong." + i, Mth.ceilLong(d));
            pd("mth.frac.d." + i, Mth.frac(d));
            p("mth.sign." + i, Mth.sign(d));
            pd("mth.square.d." + i, Mth.square(d));
            pd("mth.smoothstep." + i, Mth.smoothstep(d));
            pd("mth.smoothstepDerivative." + i, Mth.smoothstepDerivative(d));
            pf("mth.wrapDegrees.d." + i, (float) Mth.wrapDegrees(d));
            pf("mth.rotLerp.d." + i, (float) Mth.rotLerp(0.25, 10.0, d));
        }
        for (int i = 0; i < FLOATS.length; i++) {
            float f = FLOATS[i];
            p("mth.floor.f." + i, Mth.floor(f));
            p("mth.ceil.f." + i, Mth.ceil(f));
            pf("mth.abs." + i, Mth.abs(f));
            p("mth.abs.i." + i, Mth.abs((int) f));
            pf("mth.frac.f." + i, Mth.frac(f));
            pf("mth.square.f." + i, Mth.square(f));
            pf("mth.cube." + i, Mth.cube(f));
            pf("mth.wrapDegrees." + i, Mth.wrapDegrees(f));
            pf("mth.triangleWave." + i, Mth.triangleWave(f, 4.7F));
            pf("mth.clamp.f." + i, Mth.clamp(f, -3.5F, 7.25F));
            pd("mth.clamp.d." + i, Mth.clamp((double) f, -3.5, 7.25));
            pf("mth.rotLerpRad." + i, Float.isFinite(f) && Math.abs(f) < 10_000.0F
                    ? Mth.rotLerpRad(0.3F, 1.1F, f) : 0.0F);
            pf("mth.rotLerp.f." + i, Mth.rotLerp(0.3F, 1.1F, f));
        }
        p("mth.clamp.i", Mth.clamp(99, 3, 7));
        p("mth.clamp.l", Mth.clamp(-99L, 3L, 7L));
        p("mth.absMax.i", Mth.absMax(-17, 9));
        pf("mth.absMax.f", Mth.absMax(-17.5F, 9.25F));
        pd("mth.absMax.d", Mth.absMax(-17.5, 9.25));
        p("mth.chessboard", Mth.chessboardDistance(-4, 9, 11, -30));
        p("mth.floorDiv", Mth.floorDiv(-17, 5));
        p("mth.positiveCeilDiv", Mth.positiveCeilDiv(17, 5));
        p("mth.roundToward", Mth.roundToward(17, 5));
        p("mth.positiveModulo.i", Mth.positiveModulo(-17, 5));
        pf("mth.positiveModulo.f", Mth.positiveModulo(-17.5F, 5.25F));
        pd("mth.positiveModulo.d", Mth.positiveModulo(-17.5, 5.25));
        p("mth.isMultipleOf.true", Mth.isMultipleOf(91, 7) ? 1 : 0);
        p("mth.isMultipleOf.false", Mth.isMultipleOf(92, 7) ? 1 : 0);
        p("mth.equal.f", Mth.equal(1.0F, 1.000001F - 4.0F) ? 1 : 0);
        p("mth.equal.d", Mth.equal(1.0, 1.000009) ? 1 : 0);
        p("mth.packDegrees", Mth.packDegrees(-721.5F));
        pf("mth.unpackDegrees", Mth.unpackDegrees((byte) 77));
        p("mth.wrapDegrees.i", Mth.wrapDegrees(-721));
        pf("mth.wrapDegrees.l", Mth.wrapDegrees(-721L));
        p("mth.getInt.ok", Mth.getInt("-1234", 7));
        p("mth.getInt.bad", Mth.getInt("12x", 7));
        p("mth.getInt.null", Mth.getInt(null, 7));
    }

    private static void mthWrap() {
        for (int i = 0; i < DEGREES.length; i++) {
            float f = DEGREES[i];
            pf("mth.degreesDifference." + i, Mth.degreesDifference(f, 37.5F));
            pf("mth.degreesDifferenceAbs." + i, Mth.degreesDifferenceAbs(f, 37.5F));
            pf("mth.rotateIfNecessary." + i, Mth.rotateIfNecessary(f, 37.5F, 11.25F));
            pf("mth.approach." + i, Mth.approach(f, 37.5F, 11.25F));
            pf("mth.approachDegrees." + i, Mth.approachDegrees(f, 37.5F, 11.25F));
            pf("mth.wrapDegrees.loop." + i, Mth.wrapDegrees(f));
        }
    }

    private static void mthLerp() {
        for (int i = 0; i < DOUBLES.length; i++) {
            double d = DOUBLES[i];
            pd("mth.clampedLerp.d." + i, Mth.clampedLerp(d, -2.5, 8.75));
            pf("mth.clampedLerp.f." + i, Mth.clampedLerp((float) d, -2.5F, 8.75F));
            pf("mth.lerp.f." + i, Mth.lerp((float) d, -2.5F, 8.75F));
            pd("mth.lerp.d." + i, Mth.lerp(d, -2.5, 8.75));
            pd("mth.inverseLerp.d." + i, Mth.inverseLerp(d, -2.5, 8.75));
            pf("mth.inverseLerp.f." + i, Mth.inverseLerp((float) d, -2.5F, 8.75F));
            pd("mth.clampedMap.d." + i, Mth.clampedMap(d, -2.5, 8.75, 100.0, -100.0));
            pf("mth.clampedMap.f." + i, Mth.clampedMap((float) d, -2.5F, 8.75F, 100.0F, -100.0F));
            pd("mth.map.d." + i, Mth.map(d, -2.5, 8.75, 100.0, -100.0));
            pf("mth.map.f." + i, Mth.map((float) d, -2.5F, 8.75F, 100.0F, -100.0F));
            pd("mth.length2.d." + i, Mth.length(d, -3.5));
            pd("mth.lengthSquared2.d." + i, Mth.lengthSquared(d, -3.5));
            pf("mth.length2.f." + i, Mth.length((float) d, -3.5F));
            pd("mth.length3.d." + i, Mth.length(d, -3.5, 2.25));
            pd("mth.lengthSquared3.d." + i, Mth.lengthSquared(d, -3.5, 2.25));
            pf("mth.lengthSquared3.f." + i, Mth.lengthSquared((float) d, -3.5F, 2.25F));
        }
        pd("mth.lerp2", Mth.lerp2(0.25, 0.75, 1.0, 2.0, 3.0, 4.0));
        pd("mth.lerp3", Mth.lerp3(0.25, 0.5, 0.75, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0));
        pf("mth.catmullrom", Mth.catmullrom(0.375F, -1.5F, 2.25F, 7.0F, -4.5F));
        p("mth.lerpInt", Mth.lerpInt(0.375F, -13, 91));
        p("mth.lerpDiscrete", Mth.lerpDiscrete(0.375F, -13, 91));
        p("mth.lerpDiscrete.one", Mth.lerpDiscrete(1.0F, -13, 91));
        p("mth.smallestEncompassingPowerOfTwo", Mth.smallestEncompassingPowerOfTwo(1000));
        p("mth.smallestSquareSide", Mth.smallestSquareSide(1000));
        p("mth.isPowerOfTwo", Mth.isPowerOfTwo(1024) ? 1 : 0);
        p("mth.ceillog2", Mth.ceillog2(1000));
        p("mth.log2", Mth.log2(1000));
        p("mth.log2.exact", Mth.log2(1024));
        p("mth.seed", Mth.getSeed(new Vec3i(-7, 123, 4567)));
        p("mth.seed.plain", Mth.getSeed(-7, 123, 4567));
        p("mth.murmur", Mth.murmurHash3Mixer(-123456789));
        IntPredicate predicate = value -> (long) value * value >= 5000;
        p("mth.binarySearch", Mth.binarySearch(0, 400, predicate));
        p("mth.binarySearch.none", Mth.binarySearch(0, 10, value -> false));
        p("mth.binarySearch.all", Mth.binarySearch(0, 10, value -> true));
        p("mth.quantize", Mth.quantize(-123.75, 16));
        p("mth.hsvToRgb", Mth.hsvToRgb(0.31F, 0.72F, 0.83F));
        p("mth.hsvToArgb", Mth.hsvToArgb(0.31F, 0.72F, 0.83F, 128));
    }

    private static void mthBits() {
        pd("mth.vectorLerp.x", Mth.lerp(0.25, new Vec3(1.0, 2.0, 3.0), new Vec3(-4.5, 5.5, 6.25)).x);
        pd("mth.vectorLerp.y", Mth.lerp(0.25, new Vec3(1.0, 2.0, 3.0), new Vec3(-4.5, 5.5, 6.25)).y);
        pd("mth.vectorLerp.z", Mth.lerp(0.25, new Vec3(1.0, 2.0, 3.0), new Vec3(-4.5, 5.5, 6.25)).z);
    }

    private static final long[] RNG_SEEDS = {
        0L, 1L, -1L, 42L, 123456789L, -987654321L, Long.MAX_VALUE, Long.MIN_VALUE,
    };

    private static final int[] RNG_BOUNDS = {
        1, 2, 3, 7, 16, 17, 31, 32, 100, 1000, 123456789,
    };

    /**
     * Random draws, in vanilla's order. A shim that gets the arithmetic right
     * but consumes a different number of values diverges from the next draw
     * onwards, which is what these sequences catch.
     */
    private static void rng() {
        for (int s = 0; s < RNG_SEEDS.length; s++) {
            RandomSource random = RandomSource.create(RNG_SEEDS[s]);
            for (int i = 0; i < 6; i++) {
                p("rng.nextInt." + s + "." + i, random.nextInt());
            }
            for (int b = 0; b < RNG_BOUNDS.length; b++) {
                p("rng.nextIntBound." + s + "." + b, random.nextInt(RNG_BOUNDS[b]));
            }
            for (int i = 0; i < 3; i++) {
                p("rng.nextLong." + s + "." + i, random.nextLong());
                pf("rng.nextFloat." + s + "." + i, random.nextFloat());
                pd("rng.nextDouble." + s + "." + i, random.nextDouble());
                p("rng.nextBoolean." + s + "." + i, random.nextBoolean() ? 1 : 0);
                pd("rng.nextGaussian." + s + "." + i, random.nextGaussian());
            }
            p("rng.between." + s, random.nextIntBetweenInclusive(-5, 5));
            pf("rng.triangleF." + s, random.triangle(-2.5F, 7.5F));
            pd("rng.triangleD." + s, random.triangle(1.5, 8.5));
            random.consumeCount(3);
            p("rng.afterConsume." + s, random.nextInt());
            p("rng.origin." + s, random.nextInt(-10, 10));
            safe("rng.originBad." + s, () -> Integer.toString(random.nextInt(5, 5)));
            RandomSource fork = random.fork();
            p("rng.fork." + s, fork.nextInt());
            p("rng.forkOfFork." + s, fork.fork().nextInt());
            random.setSeed(RNG_SEEDS[s]);
            p("rng.reseed." + s, random.nextInt());
        }

        for (int s = 0; s < RNG_SEEDS.length; s++) {
            RandomSource random = RandomSource.create(RNG_SEEDS[s]);
            PositionalRandomFactory factory = random.forkPositional();
            StringBuilder config = new StringBuilder();
            factory.parityConfigString(config);
            ps("rng.positional.config." + s, config.toString());
            p("rng.positional.at." + s, factory.at(-7, 123, 4567).nextLong());
            p("rng.positional.atPos." + s,
                    factory.at(new BlockPos(1, -64, 30000000)).nextLong());
            p("rng.positional.hash." + s,
                    factory.fromHashOf("minecraft:overworld").nextLong());
            p("rng.positional.id." + s,
                    factory.fromHashOf(Identifier.parse("horizon:test")).nextLong());
            p("rng.positional.seed." + s, factory.fromSeed(RNG_SEEDS[s]).nextLong());
        }

        for (int s = 0; s < RNG_SEEDS.length; s++) {
            RandomSource local = RandomSource.createThreadLocalInstance(RNG_SEEDS[s]);
            p("rng.local." + s, local.nextLong());
            pd("rng.local.gauss." + s, local.nextGaussian());

            RandomSource single = new SingleThreadedRandomSource(RNG_SEEDS[s]);
            p("rng.single." + s, single.nextLong());

            RandomSource threadSafe = new ThreadSafeLegacyRandomSource(RNG_SEEDS[s]);
            p("rng.threadSafe." + s, threadSafe.nextLong());
            pd("rng.threadSafe.gauss1." + s, threadSafe.nextGaussian());
            // setSeed must not reset the cached Gaussian on this implementation.
            threadSafe.setSeed(RNG_SEEDS[s]);
            pd("rng.threadSafe.gauss2." + s, threadSafe.nextGaussian());

            RandomSource legacy = RandomSource.create(RNG_SEEDS[s]);
            pd("rng.legacy.gauss1." + s, legacy.nextGaussian());
            // setSeed does reset it on LegacyRandomSource.
            legacy.setSeed(RNG_SEEDS[s]);
            pd("rng.legacy.gauss2." + s, legacy.nextGaussian());
        }

        for (int i = 0; i < RNG_SEEDS.length; i++) {
            long value = RNG_SEEDS[i];
            p("rng.mix." + i, RandomSupport.mixStafford13(value));
            RandomSupport.Seed128bit mixed = RandomSupport.upgradeSeedTo128bit(value);
            p("rng.upgrade.lo." + i, mixed.seedLo());
            p("rng.upgrade.hi." + i, mixed.seedHi());
            ps("rng.upgrade.text." + i, mixed.toString());
            RandomSupport.Seed128bit raw = RandomSupport.upgradeSeedTo128bitUnmixed(value);
            p("rng.upgradeRaw.lo." + i, raw.seedLo());
            p("rng.upgradeRaw.hi." + i, raw.seedHi());
            RandomSupport.Seed128bit combined = raw.xor(mixed).mixed();
            p("rng.upgradeCombined.lo." + i, combined.seedLo());
            p("rng.upgradeCombined.hi." + i, combined.seedHi());
        }
        String[] names = {"", "minecraft:overworld", "horizon:fabric", "a", "\u00fc\u00f1\u00ef\u00e7\u00f8d\u00e9", "0123456789abcdef"};
        for (int i = 0; i < names.length; i++) {
            RandomSupport.Seed128bit hash = RandomSupport.seedFromHashOf(names[i]);
            p("rng.hash.lo." + i, hash.seedLo());
            p("rng.hash.hi." + i, hash.seedHi());
        }

        for (int i = 0; i < DOUBLES.length; i++) {
            pd("mth.wobble." + i, Mth.wobble(DOUBLES[i]));
        }
        for (int s = 0; s < RNG_SEEDS.length; s++) {
            UUID uuid = Mth.createInsecureUUID(RandomSource.create(RNG_SEEDS[s]));
            p("mth.uuid.msb." + s, uuid.getMostSignificantBits());
            p("mth.uuid.lsb." + s, uuid.getLeastSignificantBits());
            ps("mth.uuid.text." + s, uuid.toString());

            RandomSource random = RandomSource.create(RNG_SEEDS[s]);
            p("mth.nextInt." + s, Mth.nextInt(random, -7, 19));
            p("mth.nextIntEqual." + s, Mth.nextInt(random, 9, 9));
            p("mth.nextIntInverted." + s, Mth.nextInt(random, 19, -7));
            pf("mth.nextFloat." + s, Mth.nextFloat(random, -2.5F, 7.5F));
            pd("mth.nextDouble." + s, Mth.nextDouble(random, -2.5, 7.5));
            p("mth.randomBetweenInclusive." + s, Mth.randomBetweenInclusive(random, -7, 19));
            pf("mth.randomBetween." + s, Mth.randomBetween(random, -2.5F, 7.5F));
            pf("mth.normal." + s, Mth.normal(random, 2.5F, 0.75F));
        }
        pf("mth.nextFloatEqual", Mth.nextFloat(RandomSource.create(0L), 4.5F, 4.5F));
        pd("mth.nextDoubleEqual", Mth.nextDouble(RandomSource.create(0L), 4.5, 4.5));
    }

    /**
     * AABB coverage: construction, geometry, overlap, clipping and the
     * {@link net.minecraft.world.phys.HitResult} it produces. NaN boxes are
     * only exercised through {@code hasNaN}, where no comparison is
     * ambiguous.
     */
    private static void aabb() {
        AABB box = new AABB(-3.75, -1.25, 0.5, 7.5, 9.0, 1.5);
        pd("aabb.minX", box.minX);
        pd("aabb.minY", box.minY);
        pd("aabb.minZ", box.minZ);
        pd("aabb.maxX", box.maxX);
        pd("aabb.maxY", box.maxY);
        pd("aabb.maxZ", box.maxZ);
        p("aabb.hashCode", box.hashCode());
        p("aabb.equals.copy", box.equals(new AABB(-3.75, -1.25, 0.5, 7.5, 9.0, 1.5)) ? 1 : 0);
        p("aabb.equals.swapped", box.equals(new AABB(7.5, 9.0, 1.5, -3.75, -1.25, 0.5)) ? 1 : 0);
        p("aabb.equals.other", box.equals(new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)) ? 1 : 0);
        p("aabb.hasNaN", new AABB(Double.NaN, 0.0, 0.0, 1.0, 1.0, 1.0).hasNaN() ? 1 : 0);
        p("aabb.hasNaN.clean", box.hasNaN() ? 1 : 0);

        Vec3 span = new Vec3(-1.0, 2.0, 3.0);
        pd("aabb.fromVec.minX", new AABB(span, new Vec3(2.0, 4.0, -6.0)).minX);
        pd("aabb.fromVec.minY", new AABB(span, new Vec3(2.0, 4.0, -6.0)).minY);
        pd("aabb.fromVec.minZ", new AABB(span, new Vec3(2.0, 4.0, -6.0)).minZ);
        pd("aabb.unitCube.maxX", AABB.unitCubeFromLowerCorner(span).maxX);
        pd("aabb.encapsulating.minX", AABB.encapsulatingFullBlocks(new BlockPos(5, 0, 0), new BlockPos(-2, 3, 8)).minX);
        pd("aabb.encapsulating.maxX", AABB.encapsulatingFullBlocks(new BlockPos(5, 0, 0), new BlockPos(-2, 3, 8)).maxX);
        pd("aabb.encapsulating.maxY", AABB.encapsulatingFullBlocks(new BlockPos(5, 0, 0), new BlockPos(-2, 3, 8)).maxY);

        pd("aabb.xsize", box.getXsize());
        pd("aabb.ysize", box.getYsize());
        pd("aabb.zsize", box.getZsize());
        pd("aabb.getSize", box.getSize());
        pd("aabb.distanceToSqr.vecInside", box.distanceToSqr(new Vec3(0.0, 0.0, 0.0)));
        pd("aabb.distanceToSqr.vecOutside", box.distanceToSqr(new Vec3(20.0, 30.0, -40.0)));
        AABB other = new AABB(10.0, 10.0, 10.0, 12.0, 12.0, 12.0);
        pd("aabb.distanceToSqr.aabb", box.distanceToSqr(other));
        AABB overlapping = new AABB(5.0, 5.0, 0.0, 15.0, 15.0, 5.0);
        pd("aabb.distanceToSqr.aabbOverlap", box.distanceToSqr(overlapping));

        for (Direction.Axis axis : Direction.Axis.values()) {
            pd("aabb.min." + axis.getName(), box.min(axis));
            pd("aabb.max." + axis.getName(), box.max(axis));
        }

        Vec3[] probes = {
            new Vec3(0.0, 0.0, 0.0),
            new Vec3(-3.75, -1.25, 0.5),
            new Vec3(7.5, 9.0, 1.5),
            new Vec3(-3.76, 0.0, 0.0),
            new Vec3(0.0, 99.0, 0.0),
            new Vec3(100.0, 100.0, 100.0),
        };
        for (int i = 0; i < probes.length; i++) {
            p("aabb.contains." + i, box.contains(probes[i]) ? 1 : 0);
            pd("aabb.distanceToSqr.probe." + i, box.distanceToSqr(probes[i]));
        }

        AABB[] candidates = {
            new AABB(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0),
            new AABB(7.5, 9.0, 1.5, 10.0, 10.0, 2.0),
            new AABB(8.0, 8.0, 8.0, 9.0, 9.0, 9.0),
            new AABB(-10.0, -10.0, -10.0, -3.75, 0.0, 0.0),
        };
        for (int i = 0; i < candidates.length; i++) {
            p("aabb.intersects." + i, box.intersects(candidates[i]) ? 1 : 0);
            pd("aabb.intersect.minX." + i, box.intersect(candidates[i]).minX);
            pd("aabb.intersect.maxX." + i, box.intersect(candidates[i]).maxX);
            pd("aabb.minmax.minX." + i, box.minmax(candidates[i]).minX);
            pd("aabb.minmax.maxX." + i, box.minmax(candidates[i]).maxX);
        }
        pd("aabb.intersects.blockpos", box.intersects(new BlockPos(0, 0, 0)) ? 1 : 0);
        pd("aabb.intersects.blockposEdge", box.intersects(new BlockPos(8, 9, 2)) ? 1 : 0);
        pd("aabb.intersects.vec", box.intersects(new Vec3(-10.0, -10.0, -10.0), new Vec3(0.0, 0.0, 0.0)) ? 1 : 0);
        pd("aabb.intersects.vecFlat", box.intersects(new Vec3(-10.0, 0.0, 0.0), new Vec3(10.0, 0.0, 0.0)) ? 1 : 0);

        pd("aabb.inflate.x", box.inflate(1.5, 0.25, -2.0).minX);
        pd("aabb.inflate.max", box.inflate(1.5, 0.25, -2.0).maxZ);
        pd("aabb.inflate.all", box.inflate(2.0).minX);
        pd("aabb.deflate.x", box.deflate(1.0).minX);
        pd("aabb.contract.x", box.contract(1.0, -1.0, 0.5).minX);
        pd("aabb.contract.max", box.contract(0.5, 2.0, -0.25).maxY);
        pd("aabb.expand.x", box.expandTowards(new Vec3(-2.0, 0.0, 0.0)).minX);
        pd("aabb.expand.max", box.expandTowards(new Vec3(3.0, 0.0, 0.0)).maxX);
        pd("aabb.move.x", box.move(10.0, 20.0, 30.0).minX);
        pd("aabb.move.vec", box.move(new Vec3(1.0, 2.0, 3.0)).minY);
        pd("aabb.move.blockpos", box.move(new BlockPos(-3, 7, 1)).minZ);

        pd("aabb.center.x", box.getCenter().x);
        pd("aabb.center.y", box.getCenter().y);
        pd("aabb.center.z", box.getCenter().z);
        pd("aabb.bottomCenter.y", box.getBottomCenter().y);
        pd("aabb.minPosition", box.getMinPosition().z);
        pd("aabb.maxPosition", box.getMaxPosition().y);
        pd("aabb.ofSize.minX", AABB.ofSize(new Vec3(10.0, 20.0, 30.0), 6.0, 4.0, 10.0).minX);
        pd("aabb.ofSize.maxZ", AABB.ofSize(new Vec3(10.0, 20.0, 30.0), 6.0, 4.0, 10.0).maxZ);

        Vec3 fromWest = new Vec3(-10.0, 1.0, 1.0);
        Vec3 toWest = new Vec3(0.0, 1.0, 1.0);
        printOptionalHit(box.clip(fromWest, toWest), "aabb.clipW");
        Vec3 fromTop = new Vec3(0.0, 20.0, 1.0);
        Vec3 toTop = new Vec3(0.0, -20.0, 1.0);
        printOptionalHit(box.clip(fromTop, toTop), "aabb.clipY");
        Vec3 fromFar  = new Vec3(0.0, 20.0, -10.0);
        Vec3 toFar    = new Vec3(0.0, 20.0, 10.0);
        printOptionalHit(box.clip(fromFar, toFar), "aabb.clipZMiss");
        Vec3 fromMiss = new Vec3(-10.0, 20.0, 1.0);
        Vec3 toMiss   = new Vec3(0.0, 20.0, 1.0);
        printOptionalHit(box.clip(fromMiss, toMiss), "aabb.clipNoHit");

        // Vanilla translates every box in the iterable by the block position,
        // so the boxes passed here are block-local shapes. A unit cube at
        // world (2, 2, 2) is hit from the west at x = 2.0.
        BlockPos hitPos = new BlockPos(2, 2, 2);
        AABB unitCube = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        AABB unitCubeOffset = new AABB(-0.5, 0.0, 0.0, 0.5, 1.0, 1.0);
        Vec3 fromLocal = new Vec3(-5.0, 2.5, 2.5);
        Vec3 toLocal = new Vec3(10.0, 2.5, 2.5);
        BlockHitResult iterableHit = AABB.clip(List.of(unitCube), fromLocal, toLocal, hitPos);
        ps("aabb.clipIter.direction", iterableHit == null ? "null" : iterableHit.getDirection().getName());
        ps("aabb.clipIter.type", iterableHit == null ? "null" : iterableHit.getType().name());
        pd("aabb.clipIter.x", iterableHit == null ? 0.0 : iterableHit.getLocation().x);
        pd("aabb.clipIter.y", iterableHit == null ? 0.0 : iterableHit.getLocation().y);
        pd("aabb.clipIter.z", iterableHit == null ? 0.0 : iterableHit.getLocation().z);
        p("aabb.clipIter.inside", iterableHit == null ? -1 : (iterableHit.isInside() ? 1 : 0));
        p("aabb.clipIter.blockPosX", iterableHit == null ? -1 : iterableHit.getBlockPos().getX());
        p("aabb.clipIter.blockPosY", iterableHit == null ? -1 : iterableHit.getBlockPos().getY());
        p("aabb.clipIter.blockPosZ", iterableHit == null ? -1 : iterableHit.getBlockPos().getZ());
        BlockHitResult iterableMiss = AABB.clip(List.of(unitCube), fromMiss, toMiss, hitPos);
        ps("aabb.clipIter.miss", iterableMiss == null ? "null" : iterableMiss.getDirection().getName());
        BlockHitResult nearFirst = AABB.clip(List.of(unitCube, unitCubeOffset), fromLocal, toLocal, hitPos);
        ps("aabb.clipIter.nearFirst", nearFirst == null ? "null" : nearFirst.getDirection().getName());
        pd("aabb.clipIter.nearFirstX", nearFirst == null ? 0.0 : nearFirst.getLocation().x);
        BlockHitResult nearSecond = AABB.clip(List.of(unitCubeOffset, unitCube), fromLocal, toLocal, hitPos);
        ps("aabb.clipIter.nearSecond", nearSecond == null ? "null" : nearSecond.getDirection().getName());
        pd("aabb.clipIter.nearSecondX", nearSecond == null ? 0.0 : nearSecond.getLocation().x);

        p("aabb.collided", box.collidedAlongVector(new Vec3(5.0, 0.0, 0.0),
                List.of(new AABB(10.0, 10.0, 10.0, 12.0, 12.0, 12.0))) ? 1 : 0);
        p("aabb.collidedEmpty", box.collidedAlongVector(new Vec3(5.0, 0.0, 0.0), List.of()) ? 1 : 0);

        p("mth.rayHitWest", Mth.rayIntersectsAABB(fromWest, new Vec3(10.0, 0.0, 0.0), box) ? 1 : 0);
        p("mth.rayHitSouth", Mth.rayIntersectsAABB(new Vec3(0.0, 1.0, -10.0), new Vec3(0.0, 0.0, 10.0), box) ? 1 : 0);
        p("mth.rayAway", Mth.rayIntersectsAABB(new Vec3(-10.0, 1.0, 1.0), new Vec3(-1.0, 0.0, 0.0), box) ? 1 : 0);
        p("mth.rayOffset", Mth.rayIntersectsAABB(new Vec3(-10.0, 100.0, 100.0), new Vec3(10.0, 0.0, 0.0), box) ? 1 : 0);
        p("mth.rayInside", Mth.rayIntersectsAABB(new Vec3(0.0, 0.0, 0.0), new Vec3(1.0, 1.0, 0.0), box) ? 1 : 0);
    }

    private static void printOptionalHit(java.util.Optional<Vec3> hit, String key) {
        ps(key + ".found", hit.isPresent() ? "yes" : "no");
        if (hit.isPresent()) {
            pd(key + ".x", hit.get().x);
            pd(key + ".y", hit.get().y);
            pd(key + ".z", hit.get().z);
        }
    }

    private static void vectorMath() {
        Vec3[] vectors = {
            new Vec3(0.0, 0.0, 0.0),
            new Vec3(1.0, 0.0, 0.0),
            new Vec3(0.0, 1.0, 0.0),
            new Vec3(0.0, 0.0, 1.0),
            new Vec3(1.5, -2.25, 3.75),
            new Vec3(-1.0E-5, 1.0E-6, 9.87654321),
            new Vec3(-123456.789, 0.5, 3.0E10),
        };
        for (int i = 0; i < vectors.length; i++) {
            Vec3 v = vectors[i];
            pd("vec3.normalize." + i + ".x", v.normalize().x);
            pd("vec3.normalize." + i + ".y", v.normalize().y);
            pd("vec3.normalize." + i + ".z", v.normalize().z);
            pd("vec3.length." + i, v.length());
            pd("vec3.lengthSqr." + i, v.lengthSqr());
            pd("vec3.horizontalDistance." + i, v.horizontalDistance());
            pd("vec3.horizontalDistanceSqr." + i, v.horizontalDistanceSqr());
            p("vec3.hashCode." + i, v.hashCode());
            ps("vec3.toString." + i, v.toString());
            for (int j = 0; j < DEGREES.length; j += 3) {
                float angle = DEGREES[j];
                pd("vec3.xRot." + i + "." + j + ".y", v.xRot(angle).y);
                pd("vec3.xRot." + i + "." + j + ".z", v.xRot(angle).z);
                pd("vec3.yRot." + i + "." + j + ".x", v.yRot(angle).x);
                pd("vec3.yRot." + i + "." + j + ".z", v.yRot(angle).z);
                pd("vec3.zRot." + i + "." + j + ".x", v.zRot(angle).x);
                pd("vec3.zRot." + i + "." + j + ".y", v.zRot(angle).y);
                pd("vec3.rotation." + i + "." + j + ".x", v.rotation().x);
                pd("vec3.rotation." + i + "." + j + ".y", v.rotation().y);
                pd("vec3.directionFromRotation." + i + "." + j + ".x",
                        Vec3.directionFromRotation(angle, angle * 0.5F).x);
                pd("vec3.directionFromRotation." + i + "." + j + ".y",
                        Vec3.directionFromRotation(angle, angle * 0.5F).y);
                pd("vec3.directionFromRotation." + i + "." + j + ".z",
                        Vec3.directionFromRotation(angle, angle * 0.5F).z);
                pd("vec3.directionFromVec2." + i + "." + j + ".x",
                        Vec3.directionFromRotation(new Vec2(angle, angle * 0.5F)).x);
                pd("vec3.directionFromVec2." + i + "." + j + ".y",
                        Vec3.directionFromRotation(new Vec2(angle, angle * 0.5F)).y);
                pd("vec3.directionFromVec2." + i + "." + j + ".z",
                        Vec3.directionFromRotation(new Vec2(angle, angle * 0.5F)).z);
                pd("vec3.applyLocal." + i + "." + j + ".x",
                        Vec3.applyLocalCoordinatesToRotation(new Vec2(angle, angle * 0.5F), v).x);
                pd("vec3.applyLocal." + i + "." + j + ".y",
                        Vec3.applyLocalCoordinatesToRotation(new Vec2(angle, angle * 0.5F), v).y);
                pd("vec3.applyLocal." + i + "." + j + ".z",
                        Vec3.applyLocalCoordinatesToRotation(new Vec2(angle, angle * 0.5F), v).z);
                pd("vec3.addLocal." + i + "." + j + ".x", v.addLocalCoordinates(v).x);
                pd("vec3.addLocal." + i + "." + j + ".y", v.addLocalCoordinates(v).y);
                pd("vec3.addLocal." + i + "." + j + ".z", v.addLocalCoordinates(v).z);
            }
            for (int j = 0; j < vectors.length; j++) {
                Vec3 w = vectors[j];
                pd("vec3.distanceTo." + i + "." + j, v.distanceTo(w));
                pd("vec3.distanceToSqr." + i + "." + j, v.distanceToSqr(w));
                pd("vec3.distanceToSqr3." + i + "." + j, v.distanceToSqr(w.x, w.y, w.z));
                pd("vec3.projectedOn." + i + "." + j + ".x", v.projectedOn(w).x);
                pd("vec3.projectedOn." + i + "." + j + ".y", v.projectedOn(w).y);
                pd("vec3.projectedOn." + i + "." + j + ".z", v.projectedOn(w).z);
                p("vec3.closerThan3." + i + "." + j, v.closerThan(w, 100.0, 4.5) ? 1 : 0);
                p("vec3.closerThan." + i + "." + j, v.closerThan(w, 100.0) ? 1 : 0);
                pd("vec3.lerp." + i + "." + j + ".x", v.lerp(w, 0.625).x);
                pd("vec3.lerp." + i + "." + j + ".y", v.lerp(w, 0.625).y);
                pd("vec3.lerp." + i + "." + j + ".z", v.lerp(w, 0.625).z);
                pd("vec3.vectorTo." + i + "." + j + ".x", v.vectorTo(w).x);
                pd("vec3.dot." + i + "." + j, v.dot(w));
                pd("vec3.cross." + i + "." + j + ".x", v.cross(w).x);
                pd("vec3.cross." + i + "." + j + ".y", v.cross(w).y);
                pd("vec3.cross." + i + "." + j + ".z", v.cross(w).z);
            }
        }
        Vec3 v = new Vec3(-1.5, 2.25, 4.5);
        for (Direction.Axis axis : Direction.Axis.values()) {
            pd("vec3.get." + axis.getName(), v.get(axis));
            pd("vec3.with." + axis.getName(), v.with(axis, 9.75).get(axis));
            for (Rotation rotation : Rotation.values()) {
                pd("vec3.align." + axis.getName() + "." + rotation.getSerializedName(),
                        v.align(EnumSet.of(axis)).get(axis));
            }
        }
        for (Direction direction : Direction.values()) {
            pd("vec3.relative." + direction.getName(), v.relative(direction, 2.5).length());
        }
        pd("vec3.align.xy.x", v.align(EnumSet.of(Direction.Axis.X, Direction.Axis.Y)).x);
        pd("vec3.align.xy.y", v.align(EnumSet.of(Direction.Axis.X, Direction.Axis.Y)).y);
        pd("vec3.align.xy.z", v.align(EnumSet.of(Direction.Axis.X, Direction.Axis.Y)).z);
        for (int i = 0; i + 1 < FLOATS.length; i += 2) {
            Vec2 a = new Vec2(FLOATS[i], FLOATS[i + 1]);
            pf("vec2.normalized." + i + ".x", a.normalized().x);
            pf("vec2.normalized." + i + ".y", a.normalized().y);
            pf("vec2.length." + i, a.length());
            pf("vec2.lengthSquared." + i, a.lengthSquared());
            pf("vec2.dot." + i, a.dot(a));
            pf("vec2.distanceToSqr." + i, a.distanceToSqr(a));
            pf("vec2.scale." + i, a.scale(-1.75F).x);
            pf("vec2.add." + i, a.add(0.5F).y);
            p("vec2.equals." + i, a.equals(a) ? 1 : 0);
        }
    }

    private static void blockPos() {
        int[][] points = {
            {0, 0, 0}, {1, -1, 1}, {-1, 63, 2}, {30_000_000, -64, -30_000_000},
            {-33_554_431, 2047, 33_554_431}, {123, -456, 789}, {-7, 0, -7},
        };
        for (int i = 0; i < points.length; i++) {
            int x = points[i][0];
            int y = points[i][1];
            int z = points[i][2];
            BlockPos pos = new BlockPos(x, y, z);
            long packed = pos.asLong();
            p("blockpos.asLong." + i, packed);
            p("blockpos.asLong.static." + i, BlockPos.asLong(x, y, z));
            p("blockpos.getX." + i, BlockPos.getX(packed));
            p("blockpos.getY." + i, BlockPos.getY(packed));
            p("blockpos.getZ." + i, BlockPos.getZ(packed));
            p("blockpos.flatIndex." + i, BlockPos.getFlatIndex(packed));
            p("blockpos.hashCode." + i, pos.hashCode());
            ps("blockpos.toString." + i, pos.toString());
            ps("blockpos.short." + i, pos.toShortString());
            pd("blockpos.center." + i, pos.getCenter().distanceToSqr(Vec3.atCenterOf(pos)));
            pd("blockpos.bottomCenter." + i, pos.getBottomCenter().length());
            pd("blockpos.distSqr." + i, pos.distSqr(BlockPos.ZERO));
            pd("blockpos.distToCenterSqr." + i, pos.distToCenterSqr(0.5, 0.5, 0.5));
            p("blockpos.distManhattan." + i, pos.distManhattan(BlockPos.ZERO));
            p("blockpos.distChessboard." + i, pos.distChessboard(BlockPos.ZERO));
            p("blockpos.compare." + i, Integer.signum(pos.compareTo(BlockPos.ZERO)));
            p("blockpos.closerThan." + i, pos.closerThan(BlockPos.ZERO, 1.0E8) ? 1 : 0);
            for (Direction direction : Direction.values()) {
                p("blockpos.offset." + i + "." + direction.getName(),
                        pos.relative(direction, 3).asLong());
                p("blockpos.offsetStatic." + i + "." + direction.getName(),
                        BlockPos.offset(packed, direction));
            }
            for (Rotation rotation : Rotation.values()) {
                p("blockpos.rotate." + i + "." + rotation.getSerializedName(),
                        pos.rotate(rotation).asLong());
                p("blockpos.rotateInt." + i + "." + rotation.getSerializedName(), rotation.rotate(i, 7));
            }
            pd("blockpos.clamp." + i + ".x", pos.clampLocationWithin(new Vec3(-1.0E9, 0.0, 1.0E9)).x);
            pd("blockpos.clamp." + i + ".y", pos.clampLocationWithin(new Vec3(-1.0E9, 0.0, 1.0E9)).y);
            pd("blockpos.clamp." + i + ".z", pos.clampLocationWithin(new Vec3(-1.0E9, 0.0, 1.0E9)).z);
            p("blockpos.of." + i, BlockPos.of(packed).asLong());
        }
        p("blockpos.containing.x", BlockPos.containing(-1.5, 2.5, 3.5).getX());
        p("blockpos.containing.y", BlockPos.containing(-1.5, 2.5, 3.5).getY());
        p("blockpos.containing.z", BlockPos.containing(-1.5, 2.5, 3.5).getZ());
        p("blockpos.min", BlockPos.min(new BlockPos(1, 2, 3), new BlockPos(-1, 5, 0)).asLong());
        p("blockpos.max", BlockPos.max(new BlockPos(1, 2, 3), new BlockPos(-1, 5, 0)).asLong());
        p("blockpos.multiply", new BlockPos(1, -2, 3).multiply(-4).asLong());
        p("blockpos.cross", new BlockPos(1, 2, 3).cross(new Vec3i(4, 5, 6)).asLong());
        p("blockpos.atY", new BlockPos(1, 2, 3).atY(-9).asLong());
        p("blockpos.offsetVec", new BlockPos(1, 2, 3).offset(new Vec3i(4, 5, 6)).asLong());
        p("blockpos.subtractVec", new BlockPos(1, 2, 3).subtract(new Vec3i(4, 5, 6)).asLong());
    }

    private static void chunkPos() {
        bootstrap();
        p("chunkpos.maxCoordinate", ChunkPos.MAX_COORDINATE_VALUE);
        p("chunkpos.invalid", ChunkPos.INVALID_CHUNK_POS);
        p("chunkpos.regionSize", ChunkPos.REGION_SIZE);
        p("chunkpos.regionMaxIndex", ChunkPos.REGION_MAX_INDEX);
        int[][] chunks = {{0, 0}, {1, -1}, {-30_000_000, 30_000_000}, {1234, -5678}, {-1, 1}};
        for (int i = 0; i < chunks.length; i++) {
            ChunkPos chunk = new ChunkPos(chunks[i][0], chunks[i][1]);
            long packed = chunk.pack();
            p("chunkpos.pack." + i, packed);
            p("chunkpos.packStatic." + i, ChunkPos.pack(chunks[i][0], chunks[i][1]));
            p("chunkpos.getX." + i, ChunkPos.getX(packed));
            p("chunkpos.getZ." + i, ChunkPos.getZ(packed));
            p("chunkpos.hash." + i, ChunkPos.hash(chunks[i][0], chunks[i][1]));
            p("chunkpos.hashCode." + i, chunk.hashCode());
            p("chunkpos.isValid." + i, chunk.isValid() ? 1 : 0);
            p("chunkpos.minBlockX." + i, chunk.getMinBlockX());
            p("chunkpos.minBlockZ." + i, chunk.getMinBlockZ());
            p("chunkpos.maxBlockX." + i, chunk.getMaxBlockX());
            p("chunkpos.maxBlockZ." + i, chunk.getMaxBlockZ());
            p("chunkpos.middleBlockX." + i, chunk.getMiddleBlockX());
            p("chunkpos.middleBlockZ." + i, chunk.getMiddleBlockZ());
            p("chunkpos.regionX." + i, chunk.getRegionX());
            p("chunkpos.regionZ." + i, chunk.getRegionZ());
            p("chunkpos.regionLocalX." + i, chunk.getRegionLocalX());
            p("chunkpos.regionLocalZ." + i, chunk.getRegionLocalZ());
            p("chunkpos.blockX." + i, chunk.getBlockX(7));
            p("chunkpos.blockZ." + i, chunk.getBlockZ(-7));
            p("chunkpos.blockAt." + i, chunk.getBlockAt(1, 2, 3).asLong());
            p("chunkpos.unpack." + i, ChunkPos.unpack(packed).pack());
            ps("chunkpos.toString." + i, chunk.toString());
            p("chunkpos.distanceSquared." + i, chunk.distanceSquared(new ChunkPos(0, 0)));
            p("chunkpos.chessboard." + i, chunk.getChessboardDistance(new ChunkPos(0, 0)));
            p("chunkpos.contains." + i, chunk.contains(new BlockPos(chunk.getMinBlockX(), 0, chunk.getMinBlockZ())) ? 1 : 0);
            p("chunkpos.worldPosition." + i, chunk.getWorldPosition().asLong());
        }
    }

    private static void identifier() {
        String[] values = {
            "minecraft:stone", "stone", "a:b", "a:b/c", "minecraft:", ":path", "a:b?c", "A:B",
            "a_b:c.d-e/f", "a:b@c", "", ":", "minecraft:block/stone",
        };
        for (int i = 0; i < values.length; i++) {
            try {
                Identifier id = Identifier.parse(values[i]);
                ps("identifier.parse." + i, id.toString());
                p("identifier.hashCode." + i, id.hashCode());
            } catch (RuntimeException e) {
                ps("identifier.parse." + i, "throws:" + e.getClass().getSimpleName());
            }
            Identifier parsed = Identifier.tryParse(values[i]);
            ps("identifier.tryParse." + i, String.valueOf(parsed));
            p("identifier.validPath." + i, Identifier.isValidPath(values[i]) ? 1 : 0);
            p("identifier.validNamespace." + i, Identifier.isValidNamespace(values[i]) ? 1 : 0);
        }
        ps("identifier.default", Identifier.withDefaultNamespace("stone").toString());
        ps("identifier.of", Identifier.fromNamespaceAndPath("custom", "path/thing").toString());
        ps("identifier.withPath", Identifier.parse("a:b").withPath("c/d").toString());
        ps("identifier.withPrefix", Identifier.parse("a:b").withPrefix("c/").toString());
        ps("identifier.withSuffix", Identifier.parse("a:b").withSuffix("/d").toString());
        ps("identifier.bySeparator", Identifier.bySeparator("a/b/c", '/').toString());
        ps("identifier.short", Identifier.parse("a:b").toShortString());
        ps("identifier.debug", Identifier.parse("a:b/c").toDebugFileName());
        ps("identifier.lang", Identifier.parse("a:b/c").toLanguageKey());
        ps("identifier.langPrefix", Identifier.parse("a:b/c").toLanguageKey("item"));
        ps("identifier.langPrefixSuffix", Identifier.parse("a:b/c").toLanguageKey("item", "x"));
        ps("identifier.shortLang", Identifier.parse("a:b/c").toShortLanguageKey());
        p("identifier.compare", Integer.signum(Identifier.parse("a:b").compareTo(Identifier.parse("a:c"))));
        p("identifier.equals", Identifier.parse("a:b").equals(Identifier.fromNamespaceAndPath("a", "b")) ? 1 : 0);
        p("identifier.isAllowed", Identifier.isAllowedInIdentifier('/') ? 1 : 0);
        p("identifier.validPathChar", Identifier.validPathChar('_') ? 1 : 0);
    }

    private static void directions() {
        for (Direction direction : Direction.values()) {
            ps("direction.name." + direction.getName(), direction.getName());
            p("direction.3d." + direction.getName(), direction.get3DDataValue());
            p("direction.2d." + direction.getName(), direction.get2DDataValue());
            p("direction.stepX." + direction.getName(), direction.getStepX());
            p("direction.stepY." + direction.getName(), direction.getStepY());
            p("direction.stepZ." + direction.getName(), direction.getStepZ());
            pf("direction.toYRot." + direction.getName(), direction.toYRot());
            ps("direction.opposite." + direction.getName(), direction.getOpposite().getName());
            safe("direction.clockwise." + direction.getName(), () -> direction.getClockWise().getName());
            safe("direction.counter." + direction.getName(), () -> direction.getCounterClockWise().getName());
            ps("direction.axis." + direction.getName(), direction.getAxis().getName());
            ps("direction.axisDirection." + direction.getName(), direction.getAxisDirection().getName());
            p("direction.unitVec." + direction.getName(), direction.getUnitVec3i().hashCode());
            p("direction.facing." + direction.getName(), direction.isFacingAngle(45.0F) ? 1 : 0);
            for (Direction.Axis axis : Direction.Axis.values()) {
                safe("direction.clockwiseAxis." + direction.getName() + "." + axis.getName(),
                        () -> direction.getClockWise(axis).getName());
                safe("direction.counterAxis." + direction.getName() + "." + axis.getName(),
                        () -> direction.getCounterClockWise(axis).getName());
            }
        }
        for (float angle : DEGREES) {
            ps("direction.fromYRot." + angle, Direction.fromYRot((double) angle).getName());
            pf("direction.getYRot." + angle, Direction.getYRot(Direction.fromYRot((double) angle)));
        }
        p("direction.from3D", Direction.from3DDataValue(3).get3DDataValue());
        p("direction.from2D", Direction.from2DDataValue(2).get2DDataValue());
        ps("direction.byName", Direction.byName("east").getName());
        ps("direction.byNameBad", String.valueOf(Direction.byName("sideways")));
        ps("direction.nearest", Direction.getNearest(3, -9, 4, Direction.NORTH).getName());
        ps("direction.approximate", Direction.getApproximateNearest(0.1, -9.0, 0.2).getName());
        ps("direction.approximateVec", Direction.getApproximateNearest(new Vec3(1.0, 0.5, -3.0)).getName());
    }

    private static void rotations() {
        for (Rotation left : Rotation.values()) {
            for (Rotation right : Rotation.values()) {
                ps("rotation.rotated." + left.getSerializedName() + "." + right.getSerializedName(),
                        left.getRotated(right).getSerializedName());
            }
            for (Direction direction : Direction.values()) {
                ps("rotation.direction." + left.getSerializedName() + "." + direction.getName(),
                        left.rotate(direction).getName());
            }
            for (int x = -3; x <= 3; x++) {
                for (int period : new int[] {4, 8, 16, 7}) {
                    p("rotation.int." + left.getSerializedName() + "." + x + "." + period,
                            left.rotate(x, period));
                }
            }
        }
    }
}
