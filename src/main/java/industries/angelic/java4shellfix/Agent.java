package industries.angelic.java4shellfix;

import java.lang.instrument.Instrumentation;

/**
 * Agent entrypoint declared as {@code Agent-Class} in the jar manifest. Used only internally: when the
 * vulnerable method was already compiled before the mod ran, {@link SelfAttach} loads this jar into the
 * running JVM so {@link #agentmain} can retransform the Base64 classes (unchanged bytes) and deoptimise
 * the already-compiled code. The directive then keeps it from being recompiled with the intrinsic.
 */
public final class Agent {

    private Agent() {}

    /**
     * System property set to {@code "true"} by {@link #agentmain} once the retransform completes. Read by
     * the parent flow after self-attach. Both run in the same JVM, so the property is a reliable signal.
     */
    static final String DONE_PROPERTY = "industries.angelic.java4shellfix.agentRetransformed";

    public static void agentmain(String args, Instrumentation inst) {
        // Loaded into our own running JVM by SelfAttach. Runs in-process, so the mod's logger is live.
        Log log = Java4ShellFix.log();
        try {
            Mitigation.open()
                .addDirective();
            long t0 = System.nanoTime();
            inst.retransformClasses(
                Class.forName("java.util.Base64$Decoder"),
                Class.forName("java.util.Base64$Encoder"));
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            // The retransform deoptimises the already-compiled method. The directive keeps it from being
            // recompiled with the intrinsic. Together these remove the vulnerable path (verified: no OOB).
            System.setProperty(DONE_PROPERTY, "true");
            log.info("agentmain: retransformed Base64 classes to discard already-compiled code (" + ms + " ms).");
        } catch (Throwable t) {
            log.error("agentmain failed", t);
        }
    }
}
