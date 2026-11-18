package industries.angelic.java4shellfix;

import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * Determines whether the CPU, as seen by this JVM, supports the AVX-512 features the vulnerable Base64
 * decode stub needs (VBMI and BW).
 *
 * <p>
 * The features are read from the single {@code CPU:} line that HotSpot's {@code VM.info} diagnostic
 * command prints. That line is the JVM's own view of the processor, which is what decides whether the
 * stub is generated at all (it also reflects feature downgrades such as {@code -XX:UseAVX=2}).
 *
 * <p>
 * <strong>Only the {@code CPU:} line is read.</strong> The rest of {@code VM.info} contains the
 * process command line, which can include secrets such as the game session access token, so the full
 * output is never retained or logged.
 */
final class CpuFeatures {

    final boolean known;
    final boolean avx512Base64;

    private CpuFeatures(boolean known, boolean avx512Base64) {
        this.known = known;
        this.avx512Base64 = avx512Base64;
    }

    static CpuFeatures detect(MBeanServer mbs) {
        String cpuLine = cpuLineFromVmInfo(mbs);
        if (cpuLine == null) {
            return new CpuFeatures(false, false);
        }
        // HotSpot prints features either as "avx512_vbmi" or "avx512vbmi" depending on version.
        boolean vbmi = cpuLine.contains("avx512_vbmi") || cpuLine.contains("avx512vbmi");
        boolean bw = cpuLine.contains("avx512bw") || cpuLine.contains("avx512_bw");
        return new CpuFeatures(true, vbmi && bw);
    }

    /**
     * Invokes {@code VM.info} and returns only its {@code CPU:} line (the HotSpot-detected feature
     * list), discarding every other line. Returns {@code null} if the command is unavailable.
     */
    private static String cpuLineFromVmInfo(MBeanServer mbs) {
        try {
            ObjectName dcmd = new ObjectName("com.sun.management:type=DiagnosticCommand");
            Object out = mbs
                .invoke(dcmd, "vmInfo", new Object[] { new String[0] }, new String[] { String[].class.getName() });
            String info = String.valueOf(out);
            String result = null;
            for (String line : info.split("\n")) {
                // HotSpot's own detected-feature line. Not the "/proc/cpuinfo" echo, and never a line
                // that could contain the command line (Command Line:, jvm_args:, java_command:, ...).
                if (line.startsWith("CPU:")) {
                    result = line;
                    break;
                }
            }
            // Do not hold a reference to the full report. Let it be collected immediately.
            return result;
        } catch (Exception e) {
            return null;
        }
    }
}
