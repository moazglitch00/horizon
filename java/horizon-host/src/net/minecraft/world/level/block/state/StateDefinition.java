package net.minecraft.world.level.block.state;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Compatibility shim for Minecraft's StateDefinition.
 * Maps properties to their possible values for a block class.
 */
public class StateDefinition<B, S extends BlockState> {
    private final B owner;
    private final java.util.List<Property<?>> properties = new java.util.ArrayList<>();

    public StateDefinition(B owner) {
        this.owner = owner;
    }

    public B getOwner() {
        return owner;
    }

    public void addProperty(Property<?> property) {
        properties.add(property);
    }

    public java.util.List<Property<?>> getProperties() {
        return properties;
    }

    public <T extends Comparable<T>> Property<T> getProperty(String name) {
        for (Property<?> p : properties) {
            if (p.getName().equals(name)) {
                return (Property<T>) p;
            }
        }
        return null;
    }

    public S getPossibleValue(Property<?> property, java.lang.Comparable<?> value) {
        return null;
    }
}
