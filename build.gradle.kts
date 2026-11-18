import me.modmuss50.mpp.ReleaseType

plugins {
    java
    `maven-publish`
    id("com.diffplug.spotless") version "8.11.0"
    // Multi-platform publishing (Modrinth / CurseForge / GitHub). Configured below. Nothing publishes
    // until the `publishMods` task is invoked (release CI), so a plain local build does no publishing.
    id("me.modmuss50.mod-publish-plugin") version "2.2.1"
}

val modId: String by project
val modName: String by project
val modGroup: String by project

group = modGroup
version = resolveVersion()

repositories {
    mavenCentral()
}

fun resolveVersion(): String {
    System.getenv("VERSION")?.takeIf { it.isNotBlank() }?.let { return it }
    // Prefer the latest git tag when there is one, matching the house buildscript's behaviour.
    runCatching {
        val process = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
            .directory(rootDir)
            .redirectErrorStream(false)
            .start()
        val out = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0 && out.isNotEmpty()) {
            return out
        }
    }
    return (project.findProperty("modVersion") as String?) ?: "0.0.0"
}

java {
    toolchain {
        // Build with a modern JDK but emit Java 8 bytecode: the mod must load on the oldest runtime
        // it targets (Forge 1.7.10 on Java 8) while the vulnerable runtimes are 17+.
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
}

// Compile-only stubs for each loader's API, so the mod compiles without pulling in Minecraft or any
// loader jar (no Loom / ForgeGradle / remapping). These classes are never packaged into the mod jar.
val stubs: SourceSet by sourceSets.creating

sourceSets {
    main {
        compileClasspath += stubs.output
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 8
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all")
}

// Token-replace ${version} in the loader metadata so each file reports the real mod version.
tasks.processResources {
    val replacements = mapOf("version" to version.toString())
    inputs.properties(replacements)
    filesMatching(listOf("fabric.mod.json", "mcmod.info", "META-INF/mods.toml", "META-INF/neoforge.mods.toml")) {
        expand(replacements)
    }
}

// The main jar is universal: it carries every loader's metadata and entrypoint, and covers Fabric,
// Quilt, Forge 1.7.10-1.21 (including lwjgl3ify) and NeoForge in one file.
tasks.jar {
    manifest {
        attributes(
            // Agent-Class + retransform capability power the self-attach cleanup for a late start.
            "Agent-Class" to "industries.angelic.java4shellfix.Agent",
            "Can-Retransform-Classes" to "true",
            "Can-Redefine-Classes" to "true",
            // LaunchWrapper entrypoint (Forge 1.7.10-1.12.2, lwjgl3ify).
            "TweakClass" to "industries.angelic.java4shellfix.platform.LaunchWrapperTweaker",
            // Make legacy FML scan this tweaker jar as a mod too, so it appears in the mod list.
            "FMLCorePluginContainsFMLMod" to "true",
            "ForceLoadAsMod" to "true",
            "Automatic-Module-Name" to "industries.angelic.java4shellfix",
            "Implementation-Title" to modName,
            "Implementation-Version" to version,
            "Specification-Title" to "CVE-2026-114938 runtime mitigation"
        )
    }
}

// The -legacy jar is a LaunchWrapper-only fallback for hardened FML forks (Cleanroom) that reject the
// modern @Mod class the main jar carries. It ships only the tweaker entrypoint and no mod metadata, so
// FML finds nothing to version-check. The tweaker still runs the full mitigation.
val legacyJar = tasks.register<Jar>("legacyJar") {
    archiveClassifier.set("legacy")
    from(sourceSets.main.get().output)
    exclude(
        "**/platform/FabricInit.class",
        "**/platform/ForgeMod.class",
        "**/platform/NeoForgeMod.class",
        "fabric.mod.json",
        "mcmod.info",
        "pack.mcmeta",
        "META-INF/mods.toml",
        "META-INF/neoforge.mods.toml"
    )
    manifest {
        attributes(
            "Agent-Class" to "industries.angelic.java4shellfix.Agent",
            "Can-Retransform-Classes" to "true",
            "Can-Redefine-Classes" to "true",
            "TweakClass" to "industries.angelic.java4shellfix.platform.LaunchWrapperTweaker",
            "Automatic-Module-Name" to "industries.angelic.java4shellfix",
            "Implementation-Title" to modName,
            "Implementation-Version" to version,
            "Specification-Title" to "CVE-2026-114938 runtime mitigation (legacy)"
        )
    }
}

tasks.assemble {
    dependsOn(legacyJar)
}

spotless {
    encoding("UTF-8")
    java {
        target("src/*/java/**/*.java")
        toggleOffOn()
        importOrderFile(file("spotless/spotless.importorder"))
        removeUnusedImports()
        eclipse("4.19").configFile(file("spotless/spotless.eclipseformat.xml"))
    }
    format("misc") {
        target(".gitignore", "*.md", ".changelogs/*.md")
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        endWithNewline()
    }
}

tasks.build {
    dependsOn(tasks.named("sourcesJar"))
}

// ----------------------------------------------------------------------------------------------------
// Publishing. One universal jar goes to each platform whose project ID and token are present. GitHub
// releases whenever its token is present. Loaders and game versions come from gradle.properties. The
// `publishMods` task only runs in release CI, so configuring it here costs a normal build nothing.
// ----------------------------------------------------------------------------------------------------

fun prop(name: String): String = (project.findProperty(name) as String?)?.trim().orEmpty()
fun env(name: String): String = System.getenv(name)?.trim().orEmpty()

val gameVersionList = prop("publishGameVersions").split(",", " ", "\n").map { it.trim() }.filter { it.isNotEmpty() }
val loaderList = prop("publishLoaders").split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }
val changelogFile = rootProject.file(".changelogs/${project.version}.md")

publishMods {
    file.set(tasks.jar.flatMap { it.archiveFile })
    additionalFiles.from(legacyJar.flatMap { it.archiveFile })
    additionalFiles.from(tasks.named<Jar>("sourcesJar").flatMap { it.archiveFile })
    changelog.set(if (changelogFile.isFile) changelogFile.readText() else "See the changelog on GitHub.")
    type.set(ReleaseType.STABLE)
    displayName.set("Java4ShellFix ${project.version}")
    modLoaders.addAll(loaderList)

    val mrId = prop("modrinthProjectId")
    if (mrId.isNotEmpty() && env("MODRINTH_TOKEN").isNotEmpty()) {
        modrinth {
            projectId.set(mrId)
            accessToken.set(env("MODRINTH_TOKEN"))
            minecraftVersions.addAll(gameVersionList)
            // Works on either side on its own, so neither is strictly required.
            environment.set(CLIENT_OR_SERVER)
        }
    }

    val cfId = prop("curseForgeProjectId")
    if (cfId.isNotEmpty() && env("CURSEFORGE_TOKEN").isNotEmpty()) {
        curseforge {
            projectId.set(cfId)
            accessToken.set(env("CURSEFORGE_TOKEN"))
            minecraftVersions.addAll(gameVersionList)
            // CurseForge requires each file to declare its environment. This mod supports both.
            client.set(true)
            server.set(true)
        }
    }

    if (env("GITHUB_TOKEN").isNotEmpty() && env("GITHUB_REPOSITORY").isNotEmpty()) {
        github {
            repository.set(env("GITHUB_REPOSITORY"))
            accessToken.set(env("GITHUB_TOKEN"))
            commitish.set(env("GITHUB_SHA").ifEmpty { "main" })
        }
    }
}

// Maven publishing (the Angelic Maven). Coordinates: industries.angelic.java4shellfix:Java4ShellFix.
// Publishes the main jar, the -legacy jar and sources. The repository is only added when
// MAVEN_PUBLISHING_URL is set (release CI), so a local `publish` targets nothing by accident.
publishing {
    publications {
        create<MavenPublication>("mod") {
            from(components["java"])
            artifact(legacyJar)
            pom {
                name.set(modName)
                description.set("Runtime mitigation for CVE-2026-114938 (\"Java4Shell\").")
                url.set("https://github.com/AngelicIndustries/Java4ShellFix")
                licenses {
                    license {
                        name.set("LGPL-3.0-or-later")
                        url.set("https://www.gnu.org/licenses/lgpl-3.0.txt")
                    }
                }
            }
        }
    }
    val mavenUrl = env("MAVEN_PUBLISHING_URL")
    if (mavenUrl.isNotEmpty()) {
        repositories {
            maven {
                name = "angelic"
                url = uri(mavenUrl)
                credentials {
                    username = env("MAVEN_USER")
                    password = env("MAVEN_PASSWORD")
                }
            }
        }
    }
}

// 67minecraft publishing, per house conventions. Behind -Ppublishing because it fetches a remote script
// at configuration time. The script itself no-ops when 67minecraftProjectId is unset.
if (project.hasProperty("publishing") && prop("67minecraftProjectId").isNotEmpty()) {
    val tag = prop("publish67ScriptTag").ifEmpty { "1.0.4" }
    apply(from = "https://raw.githubusercontent.com/AngelicIndustries/67minecraft-gradle-publish/$tag/67minecraft-publish.gradle.kts")
}
