package net.minecraft.world.item;

/**
 * Compatibility shim for Minecraft's FoodProperties.
 * Properties for edible items.
 */
public final class FoodProperties {
    private final int nutrition;
    private final float saturation;
    private final boolean alwaysEdible;
    private final boolean meat;
    private final boolean fastEating;

    private FoodProperties(int nutrition, float saturation, boolean alwaysEdible, boolean meat, boolean fastEating) {
        this.nutrition = nutrition;
        this.saturation = saturation;
        this.alwaysEdible = alwaysEdible;
        this.meat = meat;
        this.fastEating = fastEating;
    }

    public int getNutrition() {
        return nutrition;
    }

    public float getSaturation() {
        return saturation;
    }

    public boolean isAlwaysEdible() {
        return alwaysEdible;
    }

    public boolean isMeat() {
        return meat;
    }

    public boolean usesEatingAnimation() {
        return !fastEating;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int nutrition;
        private float saturation;
        private boolean alwaysEdible;
        private boolean meat;
        private boolean fastEating;

        public Builder nutrition(int nutrition) {
            this.nutrition = nutrition;
            return this;
        }

        public Builder saturation(float saturation) {
            this.saturation = saturation;
            return this;
        }

        public Builder alwaysEdible() {
            this.alwaysEdible = true;
            return this;
        }

        public Builder meat() {
            this.meat = true;
            return this;
        }

        public Builder fastEating() {
            this.fastEating = true;
            return this;
        }

        public FoodProperties build() {
            return new FoodProperties(nutrition, saturation, alwaysEdible, meat, fastEating);
        }
    }
}
