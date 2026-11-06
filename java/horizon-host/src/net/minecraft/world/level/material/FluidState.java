package net.minecraft.world.level.material;

/**
 * Compatibility shim for Minecraft's FluidState.
 * Represents the state of a fluid at a position.
 */
public class FluidState {
    public static final FluidState EMPTY = new FluidState(null);

    private final Fluid fluid;

    public FluidState(Fluid fluid) {
        this.fluid = fluid;
    }

    public boolean isEmpty() {
        return fluid == null;
    }

    public Fluid getFluid() {
        return fluid;
    }

    public boolean isSource() {
        return true;
    }

    @Override
    public String toString() {
        return "FluidState[" + (fluid != null ? fluid.toString() : "empty") + "]";
    }
}
