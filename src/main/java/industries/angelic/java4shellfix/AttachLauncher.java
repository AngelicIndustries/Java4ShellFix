package industries.angelic.java4shellfix;

/**
 * Child-process entrypoint used only by {@link SelfAttach}. It runs in a short-lived helper JVM, uses
 * the Attach API to connect to the parent JVM by PID, and loads the given jar as an agent there (which
 * triggers {@link Agent#agentmain} in the parent).
 *
 * <p>
 * The Attach API ({@code com.sun.tools.attach.VirtualMachine}) is reached by reflection so this class
 * carries no compile-time dependency on {@code jdk.attach} and loads cleanly everywhere. If the module
 * is absent the reflection fails and the helper exits non-zero, which the parent treats as "could not
 * self-attach".
 *
 * <p>
 * Usage: {@code java -cp <jar> industries.angelic.java4shellfix.AttachLauncher <pid> <agentJar>}
 */
public final class AttachLauncher {

    private AttachLauncher() {}

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: AttachLauncher <pid> <agentJar>");
            System.exit(64);
        }
        String pid = args[0];
        String agentJar = args[1];
        Object vm = null;
        try {
            Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
            vm = vmClass.getMethod("attach", String.class)
                .invoke(null, pid);
            vmClass.getMethod("loadAgent", String.class)
                .invoke(vm, agentJar);
            System.out.println("attached and loaded agent into pid " + pid);
        } catch (ClassNotFoundException e) {
            System.err.println("Attach API unavailable (jdk.attach missing): " + e);
            System.exit(2);
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            System.err.println("attach failed: " + cause);
            System.exit(1);
        } finally {
            if (vm != null) {
                try {
                    vm.getClass()
                        .getMethod("detach")
                        .invoke(vm);
                } catch (Throwable ignore) {
                    // best effort
                }
            }
        }
    }
}
