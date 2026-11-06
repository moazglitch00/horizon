package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;

/**
 * Compatibility shim for Minecraft's ClientLifecycleEvents (26.3 surface).
 *
 * <p>This is the real 26.3 surface: static final {@link Event} fields for
 * each client lifecycle event.
 */
public final class ClientLifecycleEvents {
    public static final Event<ClientStarted> CLIENT_STARTED = new Event<>(ClientStarted.class);
    public static final Event<ClientStopping> CLIENT_STOPPED = new Event<>(ClientStopping.class);

    @FunctionalInterface
    public interface ClientStarted {
        void onClientStarted(Minecraft client);
    }

    @FunctionalInterface
    public interface ClientStopping {
        void onClientStopping(Minecraft client);
    }

    private ClientLifecycleEvents() {
    }
}
