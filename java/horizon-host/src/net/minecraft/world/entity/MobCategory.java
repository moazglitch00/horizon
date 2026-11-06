package net.minecraft.world.entity;

/**
 * Compatibility shim for Minecraft's MobCategory.
 * Categorizes entities by type (creature, monster, water creature, etc.).
 */
public enum MobCategory {
    CREATURE(10, "creature", 0x2B2B2B),
    MONSTER(70, "monster", 0xB04040),
    AMBIENT(15, "ambient", 0x408040),
    WATER_CREATURE(5, "water_creature", 0x4040B0),
    WATER_AMBIENT(20, "water_ambient", 0x40B0B0),
    UNDERGROUND_WATER_CREATURE(5, "underground_water_creature", 0x40B040),
    AXOLOTLS(5, "axolotls", 0xB040B0),
    MISC(25, "misc", 0x808080);

    private final int maxInstances;
    private final String name;
    private final int color;

    MobCategory(int maxInstances, String name, int color) {
        this.maxInstances = maxInstances;
        this.name = name;
        this.color = color;
    }

    public int getMaxInstances() {
        return maxInstances;
    }

    public String getName() {
        return name;
    }

    public int getColor() {
        return color;
    }

    @Override
    public String toString() {
        return name;
    }
}
