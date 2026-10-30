/**
 * A host that starts and then sleeps forever without speaking the protocol. Used to prove the
 * engine's startup timeout fires instead of hanging.
 */
public final class SleepHost {
    public static void main(String[] args) throws Exception {
        Thread.sleep(Long.MAX_VALUE);
    }
}
