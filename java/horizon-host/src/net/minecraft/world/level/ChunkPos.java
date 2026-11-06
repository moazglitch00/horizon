package net.minecraft.world.level;

import java.util.stream.Stream;
import net.minecraft.core.BlockPos;

/**
 * Compatibility shim for Minecraft's ChunkPos: a chunk coordinate.
 * Packing, region math and distance helpers match the real vanilla
 * implementation (constants read from a real 26.x jar).
 */
public class ChunkPos {
    public static final int REGION_SIZE = 32;
    public static final int REGION_MAX_INDEX = 31;
    /** Measured from a real 26.x jar after bootstrap. */
    public static final int MAX_COORDINATE_VALUE = 2097061;

    /** Sentinal from a real 26.x jar: {@code pack(1875066, 1875066)}. */
    public static final long INVALID_CHUNK_POS = pack(1875066, 1875066);

    public static final ChunkPos ZERO = new ChunkPos(0, 0);

    private final int x;
    private final int z;

    public ChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public static ChunkPos containing(BlockPos pos) {
        return new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static ChunkPos unpack(long packed) {
        return new ChunkPos(getX(packed), getZ(packed));
    }

    public static ChunkPos minFromRegion(int regionX, int regionZ) {
        return new ChunkPos(regionX << 5, regionZ << 5);
    }

    public static ChunkPos maxFromRegion(int regionX, int regionZ) {
        return new ChunkPos((regionX << 5) + REGION_MAX_INDEX, (regionZ << 5) + REGION_MAX_INDEX);
    }

    public boolean isValid() {
        return isValid(this.x, this.z);
    }

    public static boolean isValid(int x, int z) {
        return x >= -MAX_COORDINATE_VALUE
                && x <= MAX_COORDINATE_VALUE
                && z >= -MAX_COORDINATE_VALUE
                && z <= MAX_COORDINATE_VALUE;
    }

    public long pack() {
        return pack(this.x, this.z);
    }

    public static long pack(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    public static long pack(BlockPos pos) {
        return pack(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static int getX(long packed) {
        return (int) (packed & 0xFFFFFFFFL);
    }

    public static int getZ(long packed) {
        return (int) (packed >>> 32);
    }

    @Override
    public int hashCode() {
        return hash(this.x, this.z);
    }

    public static int hash(int x, int z) {
        int i = 1664525 * x + 1013904223;
        int j = 1664525 * (z ^ -559038737) + 1013904223;
        return i ^ j;
    }

    public int getMiddleBlockX() {
        return this.getBlockX(8);
    }

    public int getMiddleBlockZ() {
        return this.getBlockZ(8);
    }

    public int getMinBlockX() {
        return this.x << 4;
    }

    public int getMinBlockZ() {
        return this.z << 4;
    }

    public int getMaxBlockX() {
        return this.getBlockX(15);
    }

    public int getMaxBlockZ() {
        return this.getBlockZ(15);
    }

    public int getRegionX() {
        return this.x >> 5;
    }

    public int getRegionZ() {
        return this.z >> 5;
    }

    public static int getRegionX(long packed) {
        return getX(packed) >> 5;
    }

    public static int getRegionZ(long packed) {
        return getZ(packed) >> 5;
    }

    public int getRegionLocalX() {
        return this.x & REGION_MAX_INDEX;
    }

    public int getRegionLocalZ() {
        return this.z & REGION_MAX_INDEX;
    }

    public BlockPos getBlockAt(int x, int y, int z) {
        return new BlockPos(this.getBlockX(x), y, this.getBlockZ(z));
    }

    public int getBlockX(int x) {
        return (this.x << 4) + x;
    }

    public int getBlockZ(int z) {
        return (this.z << 4) + z;
    }

    public BlockPos getMiddleBlockPosition(int y) {
        return new BlockPos(this.getMiddleBlockX(), y, this.getMiddleBlockZ());
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= this.getMinBlockX()
                && pos.getX() <= this.getMaxBlockX()
                && pos.getZ() >= this.getMinBlockZ()
                && pos.getZ() <= this.getMaxBlockZ();
    }

    @Override
    public String toString() {
        return "[" + this.x + ", " + this.z + "]";
    }

    public BlockPos getWorldPosition() {
        return new BlockPos(this.getMinBlockX(), 0, this.getMinBlockZ());
    }

    public int getChessboardDistance(ChunkPos other) {
        return this.getChessboardDistance(other.x, other.z);
    }

    public int getChessboardDistance(int x, int z) {
        return Math.max(Math.abs(this.x - x), Math.abs(this.z - z));
    }

    public int distanceSquared(ChunkPos other) {
        return this.distanceSquared(other.x, other.z);
    }

    public int distanceSquared(long packed) {
        return this.distanceSquared(getX(packed), getZ(packed));
    }

    private int distanceSquared(int x, int z) {
        int i = x - this.x;
        int j = z - this.z;
        return i * i + j * j;
    }

    public static Stream<ChunkPos> rangeClosed(ChunkPos center, int radius) {
        return rangeClosed(
                new ChunkPos(center.x - radius, center.z - radius),
                new ChunkPos(center.x + radius, center.z + radius));
    }

    public static Stream<ChunkPos> rangeClosed(ChunkPos min, ChunkPos max) {
        int xSize = Math.abs(min.x - max.x) + 1;
        int zSize = Math.abs(min.z - max.z) + 1;
        int xMin = Math.min(min.x, max.x);
        int zMin = Math.min(min.z, max.z);
        return java.util.stream.IntStream.range(0, xSize * zSize)
                .mapToObj(index -> new ChunkPos(xMin + index % xSize, zMin + index / xSize));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChunkPos pos)) {
            return false;
        }
        return this.x == pos.x && this.z == pos.z;
    }

    public int x() {
        return this.x;
    }

    public int z() {
        return this.z;
    }
}
