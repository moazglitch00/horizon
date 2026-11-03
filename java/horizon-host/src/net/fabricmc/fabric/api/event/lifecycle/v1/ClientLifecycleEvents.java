package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;

/**
 * Minimal compatibility shim for Fabric's ClientLifecycleEvents.
 * Phase 4 of fabric-runtime: Fabric API implementation.
 */
public interface ClientLifecycleEvents {
    Event<ClientStarted> CLIENT_STARTED = Event.create();
    Event<ClientStopping> CLIENT_STOPPING = Event.create();
    Event<ClientStopped> CLIENT_STOPPED = Event.create();

    @FunctionalInterface
    interface ClientStarted {
        void onClientStarted(Object client);
    }

    @FunctionalInterface
    interface ClientStopping {
        void onClientStopping(Object client);
    }

    @FunctionalInterface
    interface ClientStopped {
        void onClientStopped(Object client);
    }
}
