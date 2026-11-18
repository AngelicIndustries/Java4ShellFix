package industries.angelic.java4shellfix;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * Decides whether the running JVM is actually exposed to CVE-2026-114938, and produces a plain-language
 * status report. All three conditions must hold for the vulnerable code path to exist:
 *
 * <ol>
 * <li>a HotSpot JVM on a vulnerable, unpatched 17.0.6+ line,</li>
 * <li>an x86-64 architecture, and</li>
 * <li>a CPU whose features (as detected by this JVM) include AVX-512 VBMI and BW.</li>
 * </ol>
 *
 * <p>
 * If {@code UseBASE64Intrinsics} is already off, or an equivalent mitigation flag is set, the stub
 * is never generated and the JVM is not exposed.
 */
final class Exposure {

    enum Reason {

        EXPOSED("exposed"),
        NOT_HOTSPOT("not a HotSpot JVM"),
        NOT_X86("not an x86-64 CPU"),
        JDK_NOT_AFFECTED("JDK line predates the vulnerable stub (before 17.0.6)"),
        JDK_PATCHED("JDK already contains the official fix"),
        NO_AVX512_VBMI("CPU lacks AVX-512 VBMI"),
        INTRINSIC_OFF("Base64 intrinsic already disabled at startup"),
        UNKNOWN_CPU("could not determine CPU features");

        final String text;

        Reason(String text) {
            this.text = text;
        }
    }

    final Reason reason;
    final JvmVersion version;
    final String arch;
    final boolean hotspot;
    final boolean x86;
    final boolean avx512;
    final boolean cpuKnown;
    final boolean intrinsicOn;

    private Exposure(Reason reason, JvmVersion version, String arch, boolean hotspot, boolean x86, boolean avx512,
        boolean cpuKnown, boolean intrinsicOn) {
        this.reason = reason;
        this.version = version;
        this.arch = arch;
        this.hotspot = hotspot;
        this.x86 = x86;
        this.avx512 = avx512;
        this.cpuKnown = cpuKnown;
        this.intrinsicOn = intrinsicOn;
    }

    boolean isExposed() {
        return reason == Reason.EXPOSED;
    }

    static Exposure detect() {
        JvmVersion version = JvmVersion.current();
        String arch = System.getProperty("os.arch", "");
        boolean x86 = "amd64".equals(arch) || "x86_64".equals(arch) || "x86-64".equals(arch);

        String vmName = System.getProperty("java.vm.name", "");
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        boolean hotspot = vmName.contains("HotSpot") || vmName.contains("OpenJDK") || hasDiagnosticCommand(mbs);

        boolean intrinsicOn = readBase64IntrinsicFlag();

        CpuFeatures cpu = CpuFeatures.detect(mbs);

        Reason reason;
        if (!hotspot) {
            reason = Reason.NOT_HOTSPOT;
        } else if (!version.inAffectedRange()) {
            reason = Reason.JDK_NOT_AFFECTED;
        } else if (version.isPatched()) {
            reason = Reason.JDK_PATCHED;
        } else if (!x86) {
            reason = Reason.NOT_X86;
        } else if (!intrinsicOn) {
            reason = Reason.INTRINSIC_OFF;
        } else if (!cpu.known) {
            reason = Reason.UNKNOWN_CPU;
        } else if (!cpu.avx512Base64) {
            reason = Reason.NO_AVX512_VBMI;
        } else {
            reason = Reason.EXPOSED;
        }

        return new Exposure(reason, version, arch, hotspot, x86, cpu.avx512Base64, cpu.known, intrinsicOn);
    }

    private static boolean hasDiagnosticCommand(MBeanServer mbs) {
        try {
            return mbs.isRegistered(new ObjectName("com.sun.management:type=DiagnosticCommand"));
        } catch (Exception e) {
            return false;
        }
    }

    /** Reads {@code UseBASE64Intrinsics} through the HotSpot diagnostic MXBean. Defaults to "on" if unknown. */
    private static boolean readBase64IntrinsicFlag() {
        try {
            com.sun.management.HotSpotDiagnosticMXBean bean = ManagementFactory
                .getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class);
            String value = bean.getVMOption("UseBASE64Intrinsics")
                .getValue();
            return !"false".equalsIgnoreCase(value);
        } catch (Exception e) {
            return true;
        }
    }

    /** A multi-line, human-readable status report describing exactly what was detected and why. */
    String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("CVE-2026-114938 (\"Java4Shell\") status for this JVM:\n");
        sb.append("  JDK           : ")
            .append(version)
            .append('\n');
        sb.append("  Architecture  : ")
            .append(arch.isEmpty() ? "unknown" : arch)
            .append('\n');
        sb.append("  HotSpot JVM   : ")
            .append(hotspot ? "yes" : "no")
            .append('\n');
        sb.append("  JDK line      : ");
        if (!version.inAffectedRange()) {
            sb.append("not affected (predates the vulnerable stub)\n");
        } else if (version.isPatched()) {
            sb.append("fixed build\n");
        } else {
            sb.append("vulnerable, no fix in this build\n");
        }
        sb.append("  CPU AVX-512 B64: ")
            .append(cpuKnown ? (avx512 ? "yes (VBMI+BW present)" : "no") : "undetermined")
            .append('\n');
        sb.append("  Base64 intrinsic at startup: ")
            .append(intrinsicOn ? "on" : "off (already mitigated)")
            .append('\n');
        sb.append("  => ");
        if (isExposed()) {
            sb.append("EXPOSED: this JVM can execute the vulnerable Base64 decode stub.");
        } else {
            sb.append("not exposed (")
                .append(reason.text)
                .append(").");
        }
        return sb.toString();
    }
}
