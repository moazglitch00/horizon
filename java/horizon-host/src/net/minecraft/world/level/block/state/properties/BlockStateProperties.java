package net.minecraft.world.level.block.state.properties;

import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * Compatibility shim for Minecraft's BlockStateProperties.
 * Provides standard property instances for common block properties.
 */
public class BlockStateProperties {
    // Horizontal facing direction (north/south/east/west)
    public static final Property<net.minecraft.core.Direction> HORIZONTAL_FACING =
            new Property<>("horizontal_facing", net.minecraft.core.Direction.class) {
                @Override
                public java.util.Collection<net.minecraft.core.Direction> getPossibleValues() {
                    return java.util.List.of(
                            net.minecraft.core.Direction.NORTH,
                            net.minecraft.core.Direction.SOUTH,
                            net.minecraft.core.Direction.WEST,
                            net.minecraft.core.Direction.EAST);
                }

                @Override
                public net.minecraft.core.Direction parse(String value) {
                    return net.minecraft.core.Direction.byName(value);
                }
            };

    // Vertical facing direction (up/down)
    public static final Property<net.minecraft.core.Direction> VERTICAL_FACING =
            new Property<>("vertical_facing", net.minecraft.core.Direction.class) {
                @Override
                public java.util.Collection<net.minecraft.core.Direction> getPossibleValues() {
                    return java.util.List.of(
                            net.minecraft.core.Direction.UP,
                            net.minecraft.core.Direction.DOWN);
                }

                @Override
                public net.minecraft.core.Direction parse(String value) {
                    return net.minecraft.core.Direction.byName(value);
                }
            };

    // Open/closed state
    public static final Property<Boolean> OPEN =
            new Property<>("open", Boolean.class) {
                @Override
                public java.util.Collection<Boolean> getPossibleValues() {
                    return java.util.List.of(true, false);
                }

                @Override
                public Boolean parse(String value) {
                    return Boolean.parseBoolean(value);
                }
            };

    // Lit/unlit state
    public static final Property<Boolean> LIT =
            new Property<>("lit", Boolean.class) {
                @Override
                public java.util.Collection<Boolean> getPossibleValues() {
                    return java.util.List.of(true, false);
                }

                @Override
                public Boolean parse(String value) {
                    return Boolean.parseBoolean(value);
                }
            };

    // Waterlogged state
    public static final Property<Boolean> WATERLOGGED =
            new Property<>("waterlogged", Boolean.class) {
                @Override
                public java.util.Collection<Boolean> getPossibleValues() {
                    return java.util.List.of(true, false);
                }

                @Override
                public Boolean parse(String value) {
                    return Boolean.parseBoolean(value);
                }
            };

    // Age (for crops)
    public static final Property<Integer> AGE =
            new Property<>("age", Integer.class) {
                @Override
                public java.util.Collection<Integer> getPossibleValues() {
                    return java.util.List.of(0, 1, 2, 3, 4, 5, 6, 7);
                }

                @Override
                public Integer parse(String value) {
                    return Integer.parseInt(value);
                }
            };

    // Distance (for redstone, etc.)
    public static final Property<Integer> DISTANCE =
            new Property<>("distance", Integer.class) {
                @Override
                public java.util.Collection<Integer> getPossibleValues() {
                    java.util.List<Integer> vals = new java.util.ArrayList<>();
                    for (int i = 0; i <= 15; i++) vals.add(i);
                    return vals;
                }

                @Override
                public Integer parse(String value) {
                    return Integer.parseInt(value);
                }
            };

    private BlockStateProperties() {}
}
