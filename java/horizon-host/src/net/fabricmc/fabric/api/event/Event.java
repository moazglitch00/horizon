package net.fabricmc.fabric.api.event;

/**
 * Minimal compatibility shim for Fabric's Event system.
 * Phase 4 of fabric-runtime: Fabric API implementation.
 */
public interface Event<T> {
    void register(T listener);

    T invoker();

    static <T> Event<T> create() {
        return new SimpleEvent<>();
    }

    class SimpleEvent<T> implements Event<T> {
        private final java.util.List<T> listeners = new java.util.ArrayList<>();

        @Override
        public void register(T listener) {
            listeners.add(listener);
        }

        @Override
        public T invoker() {
            // Return a proxy that calls all listeners
            // For now, return null; real implementation needs dynamic proxy
            return null;
        }

        public java.util.List<T> getListeners() {
            return listeners;
        }
    }
}
