package industries.angelic.java4shellfix;

import java.io.File;

/**
 * Late-recovery path. If the compiler directive was installed after C2 had already compiled the target
 * method, the directive alone does not help: the existing compiled code still contains the vulnerable
 * stub. The fix is a JVMTI class retransform, which requires a redefine-capable agent.
 *
 * <p>
 * A JVM cannot normally load an agent into itself. The standard workaround (the one ByteBuddy uses)
 * is to launch a short-lived child {@code java} process that uses the Attach API to load this same jar
 * as an agent back into the parent, where {@link Agent#agentmain} performs the retransform.
 *
 * <p>
 * This needs a runtime that ships {@code jdk.attach} and does not forbid attaching
 * ({@code -XX:+DisableAttachMechanism} / {@code -XX:-EnableDynamicAgentLoading} both block it). When it
 * cannot be done, the caller falls back to telling the operator to add {@code -XX:-UseBASE64Intrinsics}.
 */
final class SelfAttach {

    private SelfAttach() {}

    /**
     * Attempts to load {@code jar} as an agent into this JVM via a child process. Returns true on a
     * clean attach. Never throws.
     */
    static boolean loadAgentIntoSelf(File jar, Log log) {
        if (jar == null || !jar.isFile()) {
            log.warn("self-attach: cannot locate own jar; skipping late cleanup.");
            return false;
        }
        String pid = ownPid();
        if (pid == null) {
            log.warn("self-attach: cannot determine own PID; skipping late cleanup.");
            return false;
        }
        String javaBin = javaExecutable();
        if (javaBin == null) {
            log.warn("self-attach: no java executable found under java.home; skipping late cleanup.");
            return false;
        }

        try {
            // The child's classpath is this very jar: it contains AttachLauncher (uses jdk.attach) and
            // is itself the agent to load. One jar, two roles.
            ProcessBuilder pb = new ProcessBuilder(
                javaBin,
                "-cp",
                jar.getAbsolutePath(),
                "industries.angelic.java4shellfix.AttachLauncher",
                pid,
                jar.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process child = pb.start();
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(child.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    output.append(line)
                        .append('\n');
                }
            }
            boolean done = child.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            if (!done) {
                child.destroyForcibly();
                log.warn("self-attach: helper process timed out.");
                return false;
            }
            int code = child.exitValue();
            if (code == 0) {
                return true;
            }
            log.warn("self-attach: helper exited " + code + (output.length() == 0 ? "" : "; output:\n" + output));
            return false;
        } catch (Throwable t) {
            log.error("self-attach: failed to launch helper", t);
            return false;
        }
    }

    private static String ownPid() {
        try {
            // Avoid ProcessHandle (Java 9+): parse the JMX runtime name "pid@host".
            String name = java.lang.management.ManagementFactory.getRuntimeMXBean()
                .getName();
            int at = name.indexOf('@');
            String pid = at > 0 ? name.substring(0, at) : name;
            Long.parseLong(pid.trim());
            return pid.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static String javaExecutable() {
        String home = System.getProperty("java.home");
        if (home == null) {
            return null;
        }
        boolean windows = System.getProperty("os.name", "")
            .toLowerCase()
            .contains("win");
        String exe = windows ? "java.exe" : "java";
        File bin = new File(new File(home, "bin"), exe);
        return bin.isFile() ? bin.getAbsolutePath() : null;
    }
}
