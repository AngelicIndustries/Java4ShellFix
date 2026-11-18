# Java4ShellFix

Runtime mitigation for CVE-2026-114938 ("Java4Shell"), the OpenJDK AVX-512 VBMI Base64 decode heap overflow. No restart required.

> **The real fix is to update Java** (17.0.21.1 / 21.0.13.1 / 25.0.5.1 / 27.0.1.1+) **or add `-XX:-UseBASE64Intrinsics` to your JVM args.** This mod is a stopgap for when you can't do either right now.

![logo](images/logo_small.png)

[![hub](images/badges/github.png)](https://github.com/AngelicIndustries/Java4ShellFix/releases)
[![curse](images/badges/curse.png)](https://www.curseforge.com/minecraft/mc-mods/java4shell-fix)
[![modrinth](images/badges/modrinth.png)](https://modrinth.com/mod/java4shell-fix)
[![67](images/badges/67.png)](https://67minecraft.com/mod/java4shell-fix)
[![cord](images/badges/cord.png)](https://discord.gg/xAWCqGrguG)

<!-- Not published yet
[![mcmodcn](images/badges/mcmodcn.png)](https://www.mcmod.cn/)
[![maven](images/badges/maven.png)](https://maven.angelic.industries/#/releases/industries/angelic/java4shellfix/Java4ShellFix)
-->

## The vulnerability

[CVE-2026-114938](https://nvd.nist.gov/vuln/detail/CVE-2026-114938): malformed Base64 overflows HotSpot's AVX-512 decode routine and corrupts the heap, and in Minecraft any server in your list can trigger it through its favicon or chat signatures. Only OpenJDK 17.0.6+ (unpatched) on an x86-64 CPU with AVX-512 VBMI (Intel Ice Lake+, AMD Zen 4+) is exposed, so Java 8/11, ARM, and non-AVX-512 machines are safe.

## What this mod does

If the JVM is exposed, the mod installs a HotSpot compiler directive that keeps the vulnerable method out of optimised code (discarding it via a class retransform if it was already compiled), writing no JVM memory and leaving only an in-memory change that is lost on restart. It aborts startup by default when the JVM is exposed and cannot be fixed, never touches a JVM that is already safe (patched, non-x86, no AVX-512, or the mitigation flags already set), and runs anyway with `-Djava4shellfix.onFailure=warn`.

## Configuration

System properties, or `config/java4shellfix.properties` (keys without the `java4shellfix.` prefix):

| Property | Default | Meaning |
|---|---|---|
| `java4shellfix.onFailure` | `crash` | `crash` or `warn` when exposed and unmitigated. |
| `java4shellfix.selfAttach` | `true` | Allow the retransform cleanup for a late start. |
| `java4shellfix.selfTest` | `false` | Log a post-mitigation verification line. |
| `java4shellfix.debug` | `false` | Verbose (also via `MCMODDING_DEBUG_MODE`). |

## Staying protected

The mitigation is lost on restart. Update Java, or add `-XX:-UseBASE64Intrinsics` to the startup options, to stay protected permanently.

## Supported platforms

One jar for Fabric, Quilt, Forge (1.7.10 to 1.21, including lwjgl3ify) and NeoForge. Cleanroom and other hardened FML forks that reject the modern mod metadata use the `-legacy` jar from the same release.

## Building

`./gradlew build` → `build/libs/`.

## Credits

Technique reimplemented from Theori/Xint's [runtime patch for this CVE](https://github.com/theori-io/java4shell-runtime-patch-CVE-2026-114938).

## License

`LGPLv3`.

<br>

![license](images/license_small.png)
