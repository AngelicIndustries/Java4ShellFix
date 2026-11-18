package net.minecraft.launchwrapper;

import java.net.URL;
import java.net.URLClassLoader;

// Compile-only stub of LaunchWrapper's class loader. Not packaged. The real one is used at runtime.
public class LaunchClassLoader extends URLClassLoader {

    public LaunchClassLoader(URL[] sources) {
        super(sources);
    }

    public void addClassLoaderExclusion(String toExclude) {}

    public void addTransformerExclusion(String toExclude) {}
}
