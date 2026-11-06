package net.minecraft.world.level.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Compatibility shim for Minecraft's ServerLevel.
 * Server-side level with additional server-specific methods.
 */
public class ServerLevel extends Level {
    public ServerLevel(String dimension) {
        super(dimension);
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    /** Get the server. */
    public net.minecraft.server.MinecraftServer getServer() {
        return null;
    }
}
