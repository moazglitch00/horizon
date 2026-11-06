package net.minecraft.world.level.material;

/**
 * Compatibility shim for Minecraft's MapColor.
 * Represents the color used for map rendering.
 */
public class MapColor {
    public static final MapColor STONE = new MapColor(15196114, "stone");
    public static final MapColor GRASS = new MapColor(6710800, "grass");
    public static final MapColor DIRT = new MapColor(8421440, "dirt");
    public static final MapColor WOOD = new MapColor(5149460, "wood");
    public static final MapColor AIR = new MapColor(0, "air");

    private final int id;
    private final String name;

    public MapColor(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
