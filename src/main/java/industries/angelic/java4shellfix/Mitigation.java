package industries.angelic.java4shellfix;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * Applies the in-process mitigation: a compiler directive that keeps {@code Base64$Decoder.decode0}
 * (the only caller of the vulnerable decode intrinsic) out of the C2 optimising compiler and forbids
 * it from being inlined anywhere, so no compiled code ever contains the vulnerable call site. C1 and
 * the interpreter never use the Base64 intrinsics, and encoding is left untouched.
 *
 * <p>
 * This reimplements, through the same supported interface ({@code jcmd Compiler.directives_add} via
 * the DiagnosticCommand MBean), the technique published by Theori/Xint in their runtime patch for this
 * CVE. No JVM memory is written. See the README for credit.
 */
final class Mitigation {

    /** The vulnerable method: the sole caller of the AVX-512 VBMI Base64 decode intrinsic. */
    static final String TARGET = "java/util/Base64$Decoder.decode0";

    static final String DIRECTIVE = "[{ match: \"" + TARGET
        + "\", c2: { Exclude: true } },"
        + " { match: \"*.*\", inline: \"-"
        + TARGET
        + "\" }]";

    private final MBeanServer mbs;
    private final ObjectName dcmd;

    private Mitigation() throws Exception {
        this.mbs = ManagementFactory.getPlatformMBeanServer();
        this.dcmd = new ObjectName("com.sun.management:type=DiagnosticCommand");
    }

    static Mitigation open() throws Exception {
        return new Mitigation();
    }

    /** True if our directive (or an equivalent one naming the target) is already installed. */
    boolean directiveAlreadyPresent() {
        return directivesPrint().contains(TARGET);
    }

    /** Adds the compiler directive. Returns true if, afterwards, the directive is visibly in effect. */
    boolean addDirective() throws Exception {
        if (directiveAlreadyPresent()) {
            return true;
        }
        Path tmp = Files.createTempFile("java4shellfix-directive", ".json");
        try {
            Files.write(tmp, DIRECTIVE.getBytes(StandardCharsets.UTF_8));
            mbs.invoke(
                dcmd,
                "compilerDirectivesAdd",
                new Object[] { new String[] { tmp.toString() } },
                new String[] { String[].class.getName() });
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignore) {
                // best effort
            }
        }
        return directiveAlreadyPresent();
    }

    /**
     * True if the target method currently has a C2-compiled form (optimisation level 4), which is the
     * form that can contain the vulnerable intrinsic. If so, the directive alone is not enough and the
     * already-compiled code must be discarded by a retransform.
     */
    boolean targetHasC2Code() {
        String codelist = codelist();
        for (String line : codelist.split("\n")) {
            if (!line.contains("Base64$Decoder.decode0")) {
                continue;
            }
            // Compiler.codelist lines look like: "<id> <level> <...> java.util.Base64$Decoder.decode0(...)".
            // Level 4 is the C2 optimising compiler.
            String[] tokens = line.trim()
                .split("\\s+");
            if (tokens.length >= 2) {
                try {
                    if (Integer.parseInt(tokens[1]) >= 4) {
                        return true;
                    }
                } catch (NumberFormatException ignore) {
                    // Unexpected format: fall through and treat as present, to be safe.
                    return true;
                }
            }
        }
        return false;
    }

    private String directivesPrint() {
        return runDcmd("compilerDirectivesPrint");
    }

    private String codelist() {
        return runDcmd("compilerCodelist");
    }

    private String runDcmd(String op) {
        try {
            Object out = mbs
                .invoke(dcmd, op, new Object[] { new String[0] }, new String[] { String[].class.getName() });
            return String.valueOf(out);
        } catch (Exception e) {
            return "";
        }
    }
}
