package industries.angelic.java4shellfix.platform;

import net.minecraftforge.fml.common.Mod;

import industries.angelic.java4shellfix.Java4ShellFix;

/**
 * Forge entrypoint (ModLauncher, 1.14+) and NeoForge 1.20.1, which both use the net.minecraftforge @Mod
 * annotation. FML instantiates this class during mod construction. The constructor runs the mitigation.
 *
 * <p>
 * Referenced from {@code META-INF/mods.toml}. The modid must match the one declared there.
 * {@code acceptedMinecraftVersions = "*"} is ignored by modern Forge but keeps stock legacy FML
 * (which also scans this class) from rejecting the jar on version grounds. Hardened FML forks such as
 * Cleanroom still reject the modern {@code @Mod}, so they use the separate {@code -legacy} tweaker jar.
 */
@Mod(value = Java4ShellFix.ID, acceptedMinecraftVersions = "*")
public final class ForgeMod {

    public ForgeMod() {
        Platform.run();
    }
}
