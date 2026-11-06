package net.minecraft.commands;

import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import java.util.function.Supplier;

/**
 * Compatibility shim for Minecraft's CommandSourceStack.
 *
 * <p>Command source for command execution. Fabric command mods use
 * getEntity(), getPlayer(), getServer(), permissions(), sendSuccess(), etc.
 */
public class CommandSourceStack {
    public final Object source;
    public final Level level;
    public final Vec3 position;

    public CommandSourceStack(Object source, Level level, Vec3 position) {
        this.source = source;
        this.level = level;
        this.position = position;
    }

    public Object source() {
        return source;
    }

    public Object getSource() {
        return source;
    }

    public Level level() {
        return level;
    }

    public Level getLevel() {
        return level;
    }

    public Vec3 position() {
        return position;
    }

    public Vec3 getPosition() {
        return position;
    }

    /* ========================================================================
     * Entity access - CRITICAL for command mods
     */

    /** Get the entity executing the command. */
    public Entity getEntity() {
        if (source instanceof Entity) {
            return (Entity) source;
        }
        return null;
    }

    /** Get the player executing the command. */
    public Entity getPlayer() {
        if (source instanceof Entity) {
            return (Entity) source;
        }
        return null;
    }

    /** Get the server. */
    public MinecraftServer getServer() {
        if (level != null) {
            return level.getServer();
        }
        return null;
    }

    /** Get the text name of the command source. */
    public String getTextName() {
        if (source instanceof Entity) {
            return ((Entity) source).toString();
        }
        return source != null ? source.toString() : "Console";
    }

    /** Get the permission set. */
    public net.minecraft.server.permissions.PermissionSet permissions() {
        return net.minecraft.server.permissions.Permissions.ALL;
    }

    /** Send a success message. */
    public void sendSuccess(Supplier<Component> message, boolean toAll) {
        // Default: no-op
    }

    /** Send a failure message. */
    public void sendFailure(Component message) {
        // Default: no-op
    }

    /** Check if has permission level. */
    public boolean hasPermission(int level) {
        return true;
    }

    /** Get the permission level. */
    public int getPermissionLevel() {
        return 4; // Operator level
    }

    @Override
    public String toString() {
        return "CommandSourceStack[source=" + source + ", level=" + level + ", position=" + position + "]";
    }
}
