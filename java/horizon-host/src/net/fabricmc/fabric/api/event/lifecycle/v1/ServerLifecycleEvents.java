package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;

/**
 * Compatibility shim for Minecraft's ServerLifecycleEvents (26.3 surface).
 *
 * <p>This is the real 26.3 surface: static final {@link Event} fields for
 * each server lifecycle event, each carrying a typed event object.
 */
public final class ServerLifecycleEvents {
    public static final Event<ServerStarting> SERVER_STARTING = new Event<>(ServerStarting.class);
    public static final Event<ServerStarted> SERVER_STARTED = new Event<>(ServerStarted.class);
    public static final Event<ServerStopping> SERVER_STOPPING = new Event<>(ServerStopping.class);
    public static final Event<ServerStopped> SERVER_STOPPED = new Event<>(ServerStopped.class);
    public static final Event<SyncDataPackContents> SYNC_DATA_PACK_CONTENTS = new Event<>(SyncDataPackContents.class);
    public static final Event<StartDataPackReload> START_DATA_PACK_RELOAD = new Event<>(StartDataPackReload.class);
    public static final Event<EndDataPackReload> END_DATA_PACK_RELOAD = new Event<>(EndDataPackReload.class);
    public static final Event<BeforeSave> BEFORE_SAVE = new Event<>(BeforeSave.class);
    public static final Event<AfterSave> AFTER_SAVE = new Event<>(AfterSave.class);

    @FunctionalInterface
    public interface ServerStarting {
        void onServerStarting(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStarted {
        void onServerStarted(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStopping {
        void onServerStopping(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStopped {
        void onServerStopped(MinecraftServer server);
    }

    public static class SyncDataPackContents {
    }

    public static class StartDataPackReload {
    }

    public static class EndDataPackReload {
    }

    public static class BeforeSave {
    }

    public static class AfterSave {
    }

    private ServerLifecycleEvents() {
    }
}
