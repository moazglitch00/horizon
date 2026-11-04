import net.fabricmc.api.ModInitializer;

/**
 * A minimal Fabric mod entrypoint used by the host tests.
 *
 * <p>It is compiled into its own jar (never onto the host classpath) so the test
 * proves the host loads a mod class from a jar, instantiates it, and runs
 * {@link #onInitialize()}. {@code evidence()} lets the test read back the effect.
 */
public final class TestModInitializer implements ModInitializer {
    public static boolean initialized = false;
    public static String lastModId = "";
    public static int calls = 0;

    @Override
    public void onInitialize() {
        calls++;
        initialized = true;
        lastModId = "fixture-mod";
        System.out.println("INIT:fixture-mod");
    }

    public static String evidence() {
        return "initialized=" + initialized + ";lastModId=" + lastModId + ";calls=" + calls;
    }

    public static void main(String[] args) {
        new TestModInitializer().onInitialize();
        System.out.println(evidence());
    }
}
