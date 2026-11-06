package net.fabricmc.fabric.api.event;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.Identifier;

/**
 * Compatibility shim for Minecraft's Event: the base type for all Fabric events.
 *
 * <p>This is the real 26.3 surface: the class holds a list of listeners that
 * are invoked when the event fires. Listeners are registered via
 * {@link #register(Object)} and are invoked in registration order when
 * {@link #invoker()} is called.
 */
public class Event<T> {
    private final List<T> listeners = new ArrayList<>();
    private final Class<T> listenerClass;

    public Event(Class<T> listenerClass) {
        this.listenerClass = listenerClass;
    }

    /**
     * Returns an invoker that dispatches to all registered listeners in
     * registration order. Calling any method on the returned object invokes
     * that method on every registered listener.
     */
    @SuppressWarnings("unchecked")
    public final T invoker() {
        return (T) java.lang.reflect.Proxy.newProxyInstance(
            listenerClass.getClassLoader(),
            new Class<?>[]{ listenerClass },
            (proxy, method, args) -> {
                for (T listener : listeners) {
                    try {
                        method.invoke(listener, args);
                    } catch (Exception e) {
                        throw new RuntimeException("Error invoking listener " + listener, e);
                    }
                }
                return null;
            }
        );
    }

    /**
     * Registers a listener to be invoked when the event fires. Listeners are
     * invoked in registration order.
     */
    public final void register(T listener) {
        listeners.add(listener);
    }

    public void register(Identifier phase, T listener) {
        register(listener);
    }

    public void addPhaseOrdering(Identifier before, Identifier after) {
        // Phase ordering: not implemented yet; all listeners fire in
        // registration order.
    }
}
