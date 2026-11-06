import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;

/**
 * Compatibility shim for Minecraft's ClientTickEvents.
 *
 * <p>This is the real 26.3 surface: static final {@link Event} fields for
 * client tick events.
 */
public interface ClientTickEvents {
    public static final Event<StartTick> START_CLIENT_TICK = new Event<>(StartTick.class);
    public static final Event<EndTick> END_CLIENT_TICK = new Event<>(EndTick.class);

    @FunctionalInterface
    interface StartTick {
        void onStartTick(Minecraft client);
    }

    @FunctionalInterface
    interface EndTick {
        void onEndTick(Minecraft client);
    }
}
