package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;

/**
 * Minimal compatibility shim for Fabric's ServerLifecycleEvents.
 * Phase 4 of fabric-runtime: Fabric API implementation.
 */
public interface ServerLifecycleEvents {
    Event<ServerStarting> SERVER_STARTING = Event.create();
    Event<ServerStarted> SERVER_STARTED = Event.create();
    Event<ServerStopping> SERVER_STOPPING = Event.create();
    Event<ServerStopped> SERVER_STOPPED = Event.create();

    @FunctionalInterface
    interface ServerStarting {
        void onServerStarting(Object server);
    }

    @FunctionalInterface
    interface ServerStarted {
        void onServerStarted(Object server);
    }

    @FunctionalInterface
    interface ServerStopping {
        void onServerStopping(Object server);
    }

    @FunctionalInterface
    interface ServerStopped {
        void onServerStopped(Object server);
    }
}
