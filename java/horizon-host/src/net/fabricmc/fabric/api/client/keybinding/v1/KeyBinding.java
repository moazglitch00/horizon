package net.fabricmc.fabric.api.client.keybinding.v1;

/**
 * Minimal compatibility shim for Fabric's KeyBinding API.
 * Phase 4 of fabric-runtime: Fabric API implementation.
 */
public class KeyBinding {
    private final String translationKey;
    private final int keyCode;
    private final String category;
    private boolean pressed;

    public KeyBinding(String translationKey, int keyCode, String category) {
        this.translationKey = translationKey;
        this.keyCode = keyCode;
        this.category = category;
    }

    public String getTranslationKey() { return translationKey; }
    public int getKeyCode() { return keyCode; }
    public String getCategory() { return category; }
    public boolean isPressed() { return pressed; }

    public void setPressed(boolean pressed) {
        this.pressed = pressed;
    }

    @Override
    public String toString() {
        return "KeyBinding[" + translationKey + "]";
    }
}
