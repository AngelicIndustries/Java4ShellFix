package net.minecraft.launchwrapper;

import java.io.File;
import java.util.List;

// Compile-only stub of LaunchWrapper's ITweaker (Forge 1.7.10-1.12.2, lwjgl3ify, Cleanroom). Not packaged.
public interface ITweaker {

    void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile);

    void injectIntoClassLoader(LaunchClassLoader classLoader);

    String getLaunchTarget();

    String[] getLaunchArguments();
}
