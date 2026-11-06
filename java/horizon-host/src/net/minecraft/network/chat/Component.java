package net.minecraft.network.chat;

/**
 * Compatibility shim for Minecraft's Component.
 * Chat/text component for display.
 */
public class Component {
    private final String text;

    private Component(String text) {
        this.text = text;
    }

    public static Component literal(String text) {
        return new Component(text);
    }

    public String getString() {
        return text;
    }

    @Override
    public String toString() {
        return text;
    }
}
