package net.minecraft.server.permissions;

/**
 * Compatibility shim for Minecraft's PermissionSet.
 * Set of permissions for a command source.
 */
public class PermissionSet {
    private final int level;

    public PermissionSet(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean allows(int level) {
        return this.level >= level;
    }

    @Override
    public String toString() {
        return "PermissionSet[" + level + "]";
    }
}
