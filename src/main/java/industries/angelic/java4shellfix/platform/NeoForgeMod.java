package industries.angelic.java4shellfix.platform;

import net.neoforged.fml.common.Mod;

import industries.angelic.java4shellfix.Java4ShellFix;

/**
 * NeoForge entrypoint for 1.20.2+, which uses the net.neoforged @Mod annotation (distinct from the
 * net.minecraftforge one used by Forge and NeoForge 1.20.1). Referenced from
 * {@code META-INF/neoforge.mods.toml}.
 */
@Mod(Java4ShellFix.ID)
public final class NeoForgeMod {

    public NeoForgeMod() {
        Platform.run();
    }
}
