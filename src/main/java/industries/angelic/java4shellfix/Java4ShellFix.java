package industries.angelic.java4shellfix;

import java.io.File;
import java.net.URISyntaxException;

/**
 * Entry point shared by every loader. Call {@link #mitigate()} as early as the loader allows.
 *
 * <p>
 * It runs once per JVM regardless of how many times it is called (multiple loader entrypoints, a
 * nested copy of the jar, or after an external agent), so it is safe to invoke from anywhere.
 */
public final class Java4ShellFix {

    public static final String ID = "java4shellfix";
    public static final String NAME = "Java4ShellFix";

    private static volatile Log log = Log.STDERR;
    private static boolean done;

    private Java4ShellFix() {}

    /** Installs the platform logger. Called by each loader entrypoint before {@link #mitigate()}. */
    public static void setLog(Log platformLog) {
        if (platformLog != null) {
            log = platformLog;
        }
    }

    static Log log() {
        return log;
    }

    /** Runs the mitigation once. Thread-safe and idempotent. */
    public static synchronized void mitigate() {
        if (done) {
            return;
        }
        done = true;
        try {
            run();
        } catch (Java4ShellFixException crash) {
            throw crash; // deliberate: propagate so the loader aborts startup
        } catch (Throwable t) {
            log.error("unexpected failure; this JVM may still be exposed", t);
        }
    }

    private static void run() {
        Config config = Config.load();
        Exposure exposure = Exposure.detect();

        // Always report the vulnerability status of this JVM and machine, in plain language.
        for (String line : exposure.report()
            .split("\n")) {
            log.info(line);
        }

        if (!exposure.isExposed()) {
            log.info("No action needed.");
            return;
        }

        try {
            applyToExposedJvm(config);
        } catch (Java4ShellFixException crash) {
            throw crash;
        } catch (Throwable t) {
            failed(config, "mitigation threw: " + t);
        }
    }

    private static void applyToExposedJvm(Config config) throws Exception {
        Mitigation m = Mitigation.open();

        // Was the target already compiled by C2 before we got here? If so, the directive alone cannot
        // help: existing compiled code keeps the stub until it is deoptimised by a retransform.
        boolean lateStart = m.targetHasC2Code();

        boolean directivePresent = m.addDirective();
        if (!directivePresent) {
            failed(config, "could not install the compiler directive");
            return;
        }
        log.info(
            "Installed the compiler directive that keeps the vulnerable Base64 decode stub out of "
                + "optimised code.");

        // Early start: nothing had compiled the target yet, so the directive fully prevents the stub.
        if (!lateStart) {
            succeeded(config, false);
            return;
        }

        // Late start: discard the already-compiled code with a JVMTI retransform via self-attach.
        log.warn("The vulnerable method was already compiled before the mod ran; discarding it.");
        if (!config.selfAttach) {
            failed(config, "already-compiled code remains and self-attach cleanup is disabled");
            return;
        }

        File jar = ownJar();
        System.clearProperty(Agent.DONE_PROPERTY);
        boolean attached = SelfAttach.loadAgentIntoSelf(jar, log);
        boolean retransformed = "true".equals(System.getProperty(Agent.DONE_PROPERTY));
        if (attached && retransformed) {
            succeeded(config, true);
        } else {
            failed(config, "could not discard the already-compiled vulnerable code via self-attach");
        }
    }

    private static void succeeded(Config config, boolean viaAttach) {
        log.info(
            "APPLIED: the Base64 decode intrinsic is now disabled in this JVM"
                + (viaAttach ? " (after discarding already-compiled code)" : "")
                + ".");
        log.info(
            "This change is in memory only. To keep it across restarts, add "
                + "-XX:-UseBASE64Intrinsics to the JVM startup options.");
        if (config.selfTest || config.debug) {
            try {
                boolean clean = !Mitigation.open()
                    .targetHasC2Code();
                log.info("Self-test: no C2-compiled vulnerable method present = " + clean + ".");
            } catch (Exception ignore) {
                // self-test is best-effort
            }
        }
    }

    private static void failed(Config config, String why) {
        String banner = "================================================================================\n" + " "
            + NAME
            + ": this JVM is EXPOSED to CVE-2026-114938 and could not be mitigated.\n"
            + " Reason: "
            + why
            + ".\n"
            + " Fix it permanently by restarting with:  -XX:-UseBASE64Intrinsics\n"
            + " (or update to a fixed JDK: 17.0.21.1 / 21.0.13.1 / 25.0.5.1 / 27.0.1.1 or later).\n"
            + "================================================================================";
        if (config.onFailure == Config.OnFailure.WARN) {
            for (String line : banner.split("\n")) {
                log.warn(line);
            }
            log.warn("Continuing to run while exposed, because onFailure=warn is configured.");
            return;
        }
        // Default: refuse to keep running an exposed, unmitigated JVM.
        for (String line : banner.split("\n")) {
            log.error(line, null);
        }
        throw new Java4ShellFixException(
            "Refusing to run: this JVM is exposed to CVE-2026-114938 and the in-memory mitigation "
                + "could not be applied. Restart with -XX:-UseBASE64Intrinsics, or set "
                + "-Djava4shellfix.onFailure=warn to run anyway.");
    }

    /** Locates the jar this class was loaded from, for the self-attach helper. May return null. */
    static File ownJar() {
        try {
            java.security.CodeSource cs = Java4ShellFix.class.getProtectionDomain()
                .getCodeSource();
            if (cs == null || cs.getLocation() == null) {
                return null;
            }
            File f = new File(
                cs.getLocation()
                    .toURI());
            return f.isFile() ? f : null;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }
}
