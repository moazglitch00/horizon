package net.minecraft.world.level.block.state.properties;

/**
 * Compatibility shim for Minecraft's Property base class.
 * Properties represent individual aspects of a block state (e.g., facing,
 * open/closed, lit/unlit).
 */
public abstract class Property<T extends Comparable<T>> {
    private final String name;
    private final Class<T> valueClass;

    protected Property(String name, Class<T> valueClass) {
        this.name = name;
        this.valueClass = valueClass;
    }

    public String getName() {
        return name;
    }

    public Class<T> getValueClass() {
        return valueClass;
    }

    public abstract java.util.Collection<T> getPossibleValues();

    public abstract T parse(String value);

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Property<?> other = (Property<?>) obj;
        return name.equals(other.name) && valueClass.equals(other.valueClass);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, valueClass);
    }

    @Override
    public String toString() {
        return name;
    }
}
