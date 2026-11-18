package industries.angelic.java4shellfix;

/**
 * Parses the running JDK version and decides whether it is in the range affected by CVE-2026-114938.
 *
 * <p>
 * The vulnerable AVX-512 VBMI Base64 decode stub was introduced in 17.0.6. It was fixed in the
 * November 2026 security builds: 17.0.21.1, 21.0.13.1, 25.0.5.1 and 27.0.1.1. Release lines that
 * never received a fix (18, 19, 20, 22, 23, 24, 26) stay affected for their whole life.
 *
 * <p>
 * Written against {@code java.version} / {@code java.runtime.version} strings instead of
 * {@code Runtime.version()} so the class still runs on Java 8, where it simply reports "not affected".
 */
final class JvmVersion {

    final int feature;
    final int interim;
    final int update;
    final int patch;
    /** The original version string, for logging. */
    final String raw;

    private JvmVersion(int feature, int interim, int update, int patch, String raw) {
        this.feature = feature;
        this.interim = interim;
        this.update = update;
        this.patch = patch;
        this.raw = raw;
    }

    /** Reads the current runtime version from system properties. Never throws. */
    static JvmVersion current() {
        String raw = System.getProperty("java.runtime.version");
        if (raw == null || raw.isEmpty()) {
            raw = System.getProperty("java.version", "");
        }
        return parse(raw);
    }

    /**
     * Parses a version string such as {@code "17.0.21.1+12-LTS"} or legacy {@code "1.8.0_504-b01"}.
     * Unparseable input yields a zeroed version, which is treated as "not affected".
     */
    static JvmVersion parse(String raw) {
        if (raw == null) {
            return new JvmVersion(0, 0, 0, 0, "unknown");
        }
        // Keep only the numeric prefix (drop build metadata after '+', '-' or '_').
        int cut = raw.length();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '+' || c == '-' || c == '_') {
                cut = i;
                break;
            }
        }
        String[] parts = raw.substring(0, cut)
            .split("\\.");
        int[] n = new int[4];
        int fill = 0;
        for (String part : parts) {
            if (fill >= n.length) {
                break;
            }
            try {
                n[fill++] = Integer.parseInt(part.trim());
            } catch (NumberFormatException ignore) {
                // Stop at the first non-numeric component (e.g. a vendor suffix).
                break;
            }
        }
        // Legacy "1.8.0_x" scheme: the real feature number is the second component.
        if (n[0] == 1 && fill >= 2) {
            return new JvmVersion(n[1], 0, n[2], 0, raw);
        }
        return new JvmVersion(n[0], n[1], n[2], n[3], raw);
    }

    /** True once the AVX-512 Base64 decode stub exists in this line (17.0.6+, or any feature &gt; 17). */
    boolean inAffectedRange() {
        if (feature > 17) {
            return true;
        }
        if (feature == 17) {
            return interim > 0 || update >= 6;
        }
        return false;
    }

    /** The fixed security build for this release line, or {@code null} if no public fix exists yet. */
    private int[] fixedBuild() {
        switch (feature) {
            case 17:
                return new int[] { 17, 0, 21, 1 };
            case 21:
                return new int[] { 21, 0, 13, 1 };
            case 25:
                return new int[] { 25, 0, 5, 1 };
            case 27:
                return new int[] { 27, 0, 1, 1 };
            default:
                return null;
        }
    }

    /** True if this build already contains the official fix. */
    boolean isPatched() {
        int[] fixed = fixedBuild();
        if (fixed == null) {
            return false;
        }
        int[] me = { feature, interim, update, patch };
        for (int i = 0; i < 4; i++) {
            if (me[i] != fixed[i]) {
                return me[i] > fixed[i];
            }
        }
        return true; // exactly the fixed build
    }

    /** True if this JDK carries the vulnerable stub and has not been patched. */
    boolean isVulnerableLine() {
        return inAffectedRange() && !isPatched();
    }

    @Override
    public String toString() {
        return feature + "." + interim + "." + update + "." + patch + " (" + raw + ")";
    }
}
