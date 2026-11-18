package industries.angelic.java4shellfix.platform;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Fabric / Quilt entrypoint, declared under {@code preLaunch} in {@code fabric.mod.json}. The pre-launch
 * phase runs before the game starts, which is early enough to install the directive before the Base64
 * decode method is compiled.
 */
public final class FabricInit implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        Platform.run();
    }
}
