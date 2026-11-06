package net.minecraft;

/**
 * Compatibility shim for Minecraft's IdentifierException, thrown when an
 * {@link net.minecraft.resources.Identifier} is built from invalid input.
 */
public class IdentifierException extends RuntimeException {
    public IdentifierException(String message) {
        super(message);
    }

    public IdentifierException(String message, Throwable cause) {
        super(message, cause);
    }
}
