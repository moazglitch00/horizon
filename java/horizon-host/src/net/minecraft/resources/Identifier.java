package net.minecraft.resources;

import java.nio.file.Path;
import java.util.function.UnaryOperator;

import net.minecraft.IdentifierException;

/**
 * Compatibility shim for Minecraft's Identifier (the post-26.x name of
 * ResourceLocation). Parsing, validation and language-key helpers follow the
 * real vanilla implementation.
 */
public final class Identifier implements Comparable<Identifier> {
    public static final char NAMESPACE_SEPARATOR = ':';
    public static final String DEFAULT_NAMESPACE = "minecraft";
    public static final String REALMS_NAMESPACE = "realms";
    public static final String ALLOWED_NAMESPACE_CHARACTERS = "[a-z0-9_.-]";

    private final String namespace;
    private final String path;

    private Identifier(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public static Identifier fromNamespaceAndPath(String namespace, String path) {
        return new Identifier(assertValidNamespace(namespace, path), assertValidPath(namespace, path));
    }

    /** Alias kept for shims written against the older factory name. */
    public static Identifier of(String namespace, String path) {
        return fromNamespaceAndPath(namespace, path);
    }

    public static Identifier parse(String id) {
        return bySeparator(id, NAMESPACE_SEPARATOR);
    }

    public static Identifier withDefaultNamespace(String path) {
        return new Identifier(DEFAULT_NAMESPACE, assertValidPath(DEFAULT_NAMESPACE, path));
    }

    public static Identifier tryParse(String id) {
        return tryBySeparator(id, NAMESPACE_SEPARATOR);
    }

    public static Identifier tryBuild(String namespace, String path) {
        return isValidNamespace(namespace) && isValidPath(path) ? new Identifier(namespace, path) : null;
    }

    public static Identifier bySeparator(String id, char separator) {
        int i = id.indexOf(separator);
        if (i >= 0) {
            String path = id.substring(i + 1);
            if (i != 0) {
                return createUntrusted(id.substring(0, i), path);
            }
            return withDefaultNamespace(path);
        }
        return withDefaultNamespace(id);
    }

    public static Identifier tryBySeparator(String id, char separator) {
        int i = id.indexOf(separator);
        if (i >= 0) {
            String path = id.substring(i + 1);
            String namespace = i != 0 ? id.substring(0, i) : DEFAULT_NAMESPACE;
            return isValidNamespace(namespace) && isValidPath(path) ? new Identifier(namespace, path) : null;
        }
        return isValidPath(id) ? new Identifier(DEFAULT_NAMESPACE, id) : null;
    }

    public String getPath() {
        return this.path;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public Identifier withPath(String path) {
        return new Identifier(this.namespace, assertValidPath(this.namespace, path));
    }

    public Identifier withPath(UnaryOperator<String> operator) {
        return this.withPath(operator.apply(this.path));
    }

    public Identifier withPrefix(String prefix) {
        return this.withPath(prefix + this.path);
    }

    public Identifier withSuffix(String suffix) {
        return this.withPath(this.path + suffix);
    }

    @Override
    public String toString() {
        return this.namespace + NAMESPACE_SEPARATOR + this.path;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Identifier identifier)) {
            return false;
        }
        return this.namespace.equals(identifier.namespace) && this.path.equals(identifier.path);
    }

    @Override
    public int hashCode() {
        return 31 * this.namespace.hashCode() + this.path.hashCode();
    }

    @Override
    public int compareTo(Identifier other) {
        int i = this.namespace.compareTo(other.namespace);
        return i != 0 ? i : this.path.compareTo(other.path);
    }

    public Path resolveAgainst(Path directory) {
        return directory.resolve(this.namespace).resolve(this.path);
    }

    public String toDebugFileName() {
        return this.toString().replace(NAMESPACE_SEPARATOR, '_').replace('/', '_');
    }

    public String toLanguageKey() {
        return this.namespace + "." + this.path;
    }

    public String toShortLanguageKey() {
        return DEFAULT_NAMESPACE.equals(this.namespace) ? this.path : this.toLanguageKey();
    }

    public String toShortString() {
        return DEFAULT_NAMESPACE.equals(this.namespace) ? this.path : this.toString();
    }

    public String toLanguageKey(String prefix) {
        return prefix + "." + this.toLanguageKey();
    }

    public String toLanguageKey(String prefix, String suffix) {
        return prefix + "." + this.toLanguageKey() + "." + suffix;
    }

    public static boolean isAllowedInIdentifier(char c) {
        return validPathChar(c) || c == NAMESPACE_SEPARATOR;
    }

    public static boolean isValidPath(String path) {
        for (int i = 0; i < path.length(); i++) {
            if (!validPathChar(path.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidNamespace(String namespace) {
        if ("..".equals(namespace)) {
            return false;
        }
        for (int i = 0; i < namespace.length(); i++) {
            if (!validNamespaceChar(namespace.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean validPathChar(char c) {
        return c == '_'
                || c == '-'
                || c >= 'a' && c <= 'z'
                || c >= '0' && c <= '9'
                || c == '/'
                || c == '.';
    }

    private static boolean validNamespaceChar(char c) {
        return c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.';
    }

    private static String assertValidNamespace(String namespace, String path) {
        if (!isValidNamespace(namespace)) {
            throw new IdentifierException("Non [a-z0-9_.-] character in namespace of location: " + namespace + ":" + path);
        }
        return namespace;
    }

    private static String assertValidPath(String namespace, String path) {
        if (!isValidPath(path)) {
            throw new IdentifierException("Non [a-z0-9/._-] character in path of location: " + namespace + ":" + path);
        }
        return path;
    }

    private static Identifier createUntrusted(String namespace, String path) {
        return new Identifier(assertValidNamespace(namespace, path), assertValidPath(namespace, path));
    }
}
