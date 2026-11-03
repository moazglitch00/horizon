package net.minecraft.network.chat;

/**
 * Minimal compatibility shim for Minecraft's Component/Text.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public interface Component {
    String getString();

    static Component literal(String text) {
        return new LiteralComponent(text);
    }

    static Component empty() {
        return new LiteralComponent("");
    }

    record LiteralComponent(String text) implements Component {
        @Override
        public String getString() {
            return text;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
