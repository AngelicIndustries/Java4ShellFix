package net.minecraftforge.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Compile-only stub of Forge's @Mod. Not packaged. Carries both the 1.14+ member (value) and the
// legacy 1.7.10-1.12.2 member (acceptedMinecraftVersions) so one annotated class serves every Forge
// era: modern Forge reads value() and ignores the unknown legacy member. Legacy FML reads
// acceptedMinecraftVersions() so it does not reject the jar on version grounds.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {

    String value();

    String acceptedMinecraftVersions() default "*";
}
