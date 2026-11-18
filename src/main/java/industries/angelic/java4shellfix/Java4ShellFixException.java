package industries.angelic.java4shellfix;

/**
 * Thrown to abort JVM startup when this JVM is confirmed exposed to CVE-2026-114938 and the mod could
 * not neutralise the vulnerable code path (and {@code onFailure=crash}, the default). Propagated out of
 * the loader entrypoint so the server or client fails fast with a clear message instead of running
 * exposed.
 */
public final class Java4ShellFixException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public Java4ShellFixException(String message) {
        super(message);
    }
}
