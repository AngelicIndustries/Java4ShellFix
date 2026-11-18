package industries.angelic.java4shellfix;

import java.lang.reflect.Method;

/**
 * Builds a {@link Log} backed by whichever logging framework the host platform provides, found by
 * reflection so the single universal jar needs no compile-time or bundled logging dependency: SLF4J on
 * Fabric/Quilt, log4j on Forge/NeoForge. Falls back to {@link Log#STDERR} (used in bare {@code -javaagent}
 * mode, where no framework is present).
 */
public final class PlatformLog {

    private PlatformLog() {}

    public static Log best() {
        Log slf4j = trySlf4j();
        if (slf4j != null) {
            return slf4j;
        }
        Log log4j = tryLog4j();
        if (log4j != null) {
            return log4j;
        }
        return Log.STDERR;
    }

    private static Log trySlf4j() {
        try {
            Class<?> factory = Class.forName("org.slf4j.LoggerFactory");
            Object logger = factory.getMethod("getLogger", String.class)
                .invoke(null, Java4ShellFix.NAME);
            Class<?> loggerClass = Class.forName("org.slf4j.Logger");
            return new Reflective(
                logger,
                loggerClass.getMethod("info", String.class),
                loggerClass.getMethod("warn", String.class),
                loggerClass.getMethod("error", String.class, Throwable.class));
        } catch (Throwable t) {
            return null;
        }
    }

    private static Log tryLog4j() {
        try {
            Class<?> manager = Class.forName("org.apache.logging.log4j.LogManager");
            Object logger = manager.getMethod("getLogger", String.class)
                .invoke(null, Java4ShellFix.NAME);
            Class<?> loggerClass = Class.forName("org.apache.logging.log4j.Logger");
            return new Reflective(
                logger,
                loggerClass.getMethod("info", String.class),
                loggerClass.getMethod("warn", String.class),
                loggerClass.getMethod("error", String.class, Throwable.class));
        } catch (Throwable t) {
            return null;
        }
    }

    /** A {@link Log} that forwards to a framework logger through cached reflected methods. */
    private static final class Reflective implements Log {

        private final Object logger;
        private final Method info;
        private final Method warn;
        private final Method errorWithThrowable;

        Reflective(Object logger, Method info, Method warn, Method errorWithThrowable) {
            this.logger = logger;
            this.info = info;
            this.warn = warn;
            this.errorWithThrowable = errorWithThrowable;
        }

        @Override
        public void info(String message) {
            invoke(info, message, null);
        }

        @Override
        public void warn(String message) {
            invoke(warn, message, null);
        }

        @Override
        public void error(String message, Throwable error) {
            invoke(errorWithThrowable, message, error);
        }

        private void invoke(Method m, String message, Throwable error) {
            try {
                if (m.getParameterCount() == 2) {
                    m.invoke(logger, message, error);
                } else {
                    m.invoke(logger, message);
                }
            } catch (Throwable t) {
                // Never let logging failures hide the mitigation result.
                Log.STDERR.info(message);
            }
        }
    }
}
