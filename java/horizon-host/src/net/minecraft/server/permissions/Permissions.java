package net.minecraft.server.permissions;

/**
 * Compatibility shim for Minecraft's Permissions.
 * Pre-defined permission sets.
 */
public final class Permissions {
    public static final PermissionSet ALL = new PermissionSet(4);
    public static final PermissionSet OPERATOR = new PermissionSet(4);
    public static final PermissionSet PLAYER = new PermissionSet(0);
    public static final PermissionSet NONE = new PermissionSet(-1);

    private Permissions() {}
}
