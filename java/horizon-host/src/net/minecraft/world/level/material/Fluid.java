package net.minecraft.world.level.material;

/**
 * Compatibility shim for Minecraft's Fluid.
 * Base class for all fluids (water, lava, etc.).
 */
public class Fluid {
    public static final Fluid WATER = new Fluid("water");
    public static final Fluid LAVA = new Fluid("lava");

    private final String name;

    public Fluid(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
