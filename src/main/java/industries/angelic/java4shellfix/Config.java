package industries.angelic.java4shellfix;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration, read once. Values come from, in order of precedence: a JVM system property, then
 * {@code config/java4shellfix.properties} relative to the working directory, then a built-in default.
 * No Forge/Fabric config API is used, so the same code runs on every platform and as a bare agent.
 */
final class Config {

    /** What to do when the JVM is confirmed exposed and the mod could not neutralise the stub. */
    enum OnFailure {
        /** Halt the JVM. The default: an exposed, unmitigated server should not keep running. */
        CRASH,
        /** Log a prominent warning and keep running. */
        WARN
    }

    final OnFailure onFailure;
    /** Run a self-test decode after mitigating and log the measured result. Off by default. */
    final boolean selfTest;
    /** Attempt the late self-attach cleanup when the directive was applied too late. On by default. */
    final boolean selfAttach;
    /** Verbose diagnostics. Also enabled by the MCMODDING_DEBUG_MODE env var, per house convention. */
    final boolean debug;

    private Config(OnFailure onFailure, boolean selfTest, boolean selfAttach, boolean debug) {
        this.onFailure = onFailure;
        this.selfTest = selfTest;
        this.selfAttach = selfAttach;
        this.debug = debug;
    }

    static Config load() {
        Properties file = new Properties();
        File f = new File("config", "java4shellfix.properties");
        if (f.isFile()) {
            try (InputStream in = new FileInputStream(f)) {
                file.load(in);
            } catch (Exception ignore) {
                // A broken config file must never stop the mitigation. Fall back to defaults.
            }
        }

        OnFailure onFailure = "warn".equalsIgnoreCase(get(file, "onFailure", "crash")) ? OnFailure.WARN
            : OnFailure.CRASH;
        boolean selfTest = bool(get(file, "selfTest", "false"));
        boolean selfAttach = bool(get(file, "selfAttach", "true"));
        boolean debug = bool(get(file, "debug", "false")) || System.getenv("MCMODDING_DEBUG_MODE") != null;
        return new Config(onFailure, selfTest, selfAttach, debug);
    }

    private static String get(Properties file, String key, String def) {
        String sys = System.getProperty("java4shellfix." + key);
        if (sys != null && !sys.isEmpty()) {
            return sys;
        }
        return file.getProperty(key, def);
    }

    private static boolean bool(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }
}
