package net.minecraft.world.level.difficulty;

/**
 * Compatibility shim for Minecraft's Difficulty enum.
 */
public enum Difficulty {
    PEACEFUL(0, "peaceful"),
    EASY(1, "easy"),
    NORMAL(2, "normal"),
    HARD(3, "hard");

    private final int id;
    private final String name;

    Difficulty(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public static Difficulty byId(int id) {
        for (Difficulty d : values()) {
            if (d.id == id) return d;
        }
        return NORMAL;
    }

    public static Difficulty byName(String name) {
        for (Difficulty d : values()) {
            if (d.name.equals(name)) return d;
        }
        return NORMAL;
    }

    @Override
    public String toString() {
        return name;
    }
}
