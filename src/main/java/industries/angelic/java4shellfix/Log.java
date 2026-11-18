package industries.angelic.java4shellfix;

/**
 * Minimal logging seam. Each loader entrypoint installs an adapter that forwards to the platform
 * logger (SLF4J on Fabric, log4j on Forge/NeoForge). The default writes to {@code System.err} so the
 * mod also works as a bare {@code -javaagent} with no mod loader present.
 */
public interface Log {

    void info(String message);

    void warn(String message);

    void error(String message, Throwable error);

    /** Fallback used until a loader installs its own adapter. */
    Log STDERR = new Log() {

        private void out(String level, String message) {
            System.err.println("[" + Java4ShellFix.NAME + "/" + level + "] " + message);
            System.err.flush();
        }

        @Override
        public void info(String message) {
            out("INFO", message);
        }

        @Override
        public void warn(String message) {
            out("WARN", message);
        }

        @Override
        public void error(String message, Throwable error) {
            out("ERROR", message);
            if (error != null) {
                error.printStackTrace();
            }
        }
    };
}
