package industries.angelic.java4shellfix.platform;

import java.io.File;
import java.util.List;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

/**
 * LaunchWrapper entrypoint for the legacy stack (Forge 1.7.10-1.12.2, and modern-Java variants such as
 * lwjgl3ify and Cleanroom). A tweaker is constructed and invoked very early in startup, before the game
 * classes load. The same {@link ITweaker} contract spans 1.7.10 through 1.12.2, so one class serves all.
 *
 * <p>
 * Declared via the {@code TweakClass} manifest attribute. The mitigation runs in
 * {@link #injectIntoClassLoader}, the earliest point where it is reliably safe to touch management beans.
 */
public final class LaunchWrapperTweaker implements ITweaker {

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        // no options needed
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        Platform.run();
    }

    @Override
    public String getLaunchTarget() {
        return null; // this tweaker does not launch the game; it only runs alongside the real one
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }
}
