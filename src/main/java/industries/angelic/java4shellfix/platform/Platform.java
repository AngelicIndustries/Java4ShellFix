package industries.angelic.java4shellfix.platform;

import industries.angelic.java4shellfix.Java4ShellFix;
import industries.angelic.java4shellfix.PlatformLog;

/** Shared glue for every loader entrypoint: install the platform logger, then run the mitigation once. */
final class Platform {

    private Platform() {}

    static void run() {
        Java4ShellFix.setLog(PlatformLog.best());
        Java4ShellFix.mitigate();
    }
}
