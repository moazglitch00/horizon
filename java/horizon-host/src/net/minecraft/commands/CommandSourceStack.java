package net.minecraft.commands;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Minimal compatibility shim for Minecraft's CommandSourceStack.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
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

    @Override
    public String toString() {
        return "CommandSourceStack[source=" + source + ", level=" + level + ", position=" + position + "]";
    }
}
