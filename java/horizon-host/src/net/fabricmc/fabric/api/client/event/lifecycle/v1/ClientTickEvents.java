package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;

/**
 * Minimal compatibility shim for Fabric's ClientTickEvents.
 * Phase 4 of fabric-runtime: Fabric API implementation.
 */
public interface ClientTickEvents {
    Event<StartTick> START_CLIENT_TICK = Event.create();
    Event<EndTick> END_CLIENT_TICK = Event.create();

    @FunctionalInterface
    interface StartTick {
        void onStartTick(Object client);
    }

    @FunctionalInterface
    interface EndTick {
        void onEndTick(Object client);
    }
}
