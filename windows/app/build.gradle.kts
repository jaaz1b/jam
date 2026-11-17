// Jam for Windows: Orpheon's app (android/base/app, Jam's Android app) compiled for the PC with
// Compose Multiplatform. The UI, the looks (Classic, Immersive), the extensions and the Spotify API
// are the Android app's own source files; what Android gives them (storage, resources, the player,
// notifications, the hidden Web Player) comes from this module: src/main/kotlin/android/** and
// androidx/** are small PC versions of the Android APIs they call, src/main/kotlin/app/jam/desktop
// the PC side (the window, the hidden Spotify engine, libmpv, Discord). Android-only files of the
// app (widgets, Android Auto, the watch, the TV) are left out: see android-only.txt.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xskip-prerelease-check", "-nowarn")
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.ui.ExperimentalComposeUiApi",
            "androidx.compose.animation.ExperimentalAnimationApi",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

val androidApp = rootDir.resolve("../android/base/app")
val androidHost = rootDir.resolve("../android/base/host-android")
val androidOnly: List<String> = file("android-only.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

val jamAndroid = rootDir.resolve("../ytm/src-tauri/gen/android/app/src/main")
/** Jam for Android's own files the PC uses as they are (Discord's presence, its settings page and sources). */
val jamAndroidFiles = listOf("Discord.kt", "Presence.kt", "Prefs.kt", "Player.kt", "SettingsActivity.kt", "DiscordCallbackActivity.kt", "Sources.kt", "CoverStore.kt")

/**
 * The Android sources, as compiled here: the app's and the host's (less android-only.txt) and Jam for
 * Android's few shared files, with the small PC changes of patches.txt applied (each line:
 * path ⟶ regex ⟶ replacement, separated by " => "). The originals are never modified.
 */
val androidSources by tasks.registering(Sync::class) {
    val patches = file("patches.txt")
    inputs.file(patches)
    from(androidApp.resolve("src/main/java")) { exclude(androidOnly) }
    from(androidHost.resolve("src/main/kotlin")) { exclude(androidOnly) }
    from(jamAndroid.resolve("java")) { include(jamAndroidFiles.map { "app/jam/android/$it" }) }
    into(layout.buildDirectory.dir("generated/android-src"))
    val rules = patches.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split(Regex(" =>(?: |$)")) }
    eachFile {
        val mine = rules.filter { r -> r[0] == path || r[0] == "*" }
        if (mine.isNotEmpty()) filter { line -> mine.fold(line) { l, r -> l.replace(Regex(r[1]), r.getOrElse(2) { "" }.replace("\\n", "\n")) } }
    }
}

sourceSets {
    main {
        kotlin.srcDir(androidSources)
        // The app's res/ (strings in every language, drawables, fonts, raw), read at run time by
        // the resource shim (android/content/res, androidx/compose/ui/res).
        resources.srcDir(layout.buildDirectory.dir("generated/res"))
    }
}

/** Copies the Android app's res/ into the jar (as res/...), for the resource shim. */
val copyRes by tasks.registering(Copy::class) {
    // The host library's resources first (its values files renamed so they sit beside the app's), then the app's.
    from(androidHost.resolve("src/main/res")) { into("res"); eachFile { if (relativePath.segments[1].startsWith("values")) name = "host_$name" } }
    from(androidApp.resolve("src/main/res")) { into("res") }
    // The bundled pages and data (assets/): the app's and Jam for Android's (its settings page).
    from(androidApp.resolve("src/main/assets")) { into("assets") }
    from(jamAndroid.resolve("assets")) { into("assets") }
    // The app overrides the host where both have a file (as Android merges an app over its libraries).
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    into(layout.buildDirectory.dir("generated/res"))
}
/** R: one id per resource, generated from res/ (windows/buildSrc RGen). */
val genR by tasks.registering {
    dependsOn(copyRes)
    val res = layout.buildDirectory.dir("generated/res/res")
    val srcs = listOf(androidApp.resolve("src/main/java"), androidHost.resolve("src/main/kotlin"))
    val out = layout.buildDirectory.dir("generated/r")
    inputs.dir(res); srcs.forEach { inputs.dir(it) }; outputs.dir(out); outputs.dir(layout.buildDirectory.dir("generated/rnames"))
    doLast { RGen.generate(res.get().asFile, srcs, out.get().asFile, listOf("app.myspoti.engine", "app.myspoti.host.android")) }
}
/** BuildConfig of the app module, as Android's build makes it (the PC's version; no Android updater). */
val genBuildConfig by tasks.registering {
    val out = layout.buildDirectory.dir("generated/buildconfig")
    val version = (findProperty("jamWinVersion") as String?) ?: "1.1.0"
    inputs.property("version", version); outputs.dir(out)
    doLast {
        val d = out.get().asFile.resolve("app/myspoti/engine").apply { mkdirs() }
        d.resolve("BuildConfig.kt").writeText(
            "package app.myspoti.engine\n\nobject BuildConfig {\n" +
            "    const val DEBUG = false\n    const val APPLICATION_ID = \"app.jam.android\"\n    const val BUILD_TYPE = \"release\"\n" +
            "    const val VERSION_CODE = 1\n    const val VERSION_NAME = \"$version\"\n    const val UPDATE_REPO = \"\"\n" +
            "    const val APK_NAME = \"Jam\"\n    const val ORPHEON_VERSION = \"1.0.0\"\n    const val FLAVOR = \"\"\n}\n")
    }
}
sourceSets.main {
    kotlin.srcDir(genBuildConfig)
    kotlin.srcDir(genR)
    resources.srcDir(layout.buildDirectory.dir("generated/rnames"))
}
tasks.named("processResources") { dependsOn(copyRes, genR) }

dependencies {
    implementation(project(":api-spotify"))
    implementation(project(":api-web"))
    implementation(project(":api-ytmusic"))
    implementation(project(":engine"))
    implementation(project(":extensions"))
    implementation(project(":catalog"))
    implementation(project(":automix"))
    implementation(project(":pass"))
    implementation(project(":host-cdp"))

    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.animation)
    implementation(compose.material)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.10.0-rc01")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("dev.chrisbanes.haze:haze:1.3.1")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")

    implementation(libs.coroutines.swing)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.brotli)
    implementation(libs.okhttp.zstd)
    implementation("org.json:json:20240303")
    // Android's XmlPullParser (android.util.Xml).
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("org.mozilla:rhino:1.8.1")
    implementation("com.microsoft.onnxruntime:onnxruntime:1.23.2")
    implementation("com.google.zxing:core:3.5.3")
    // libmpv (the player, as Jam's YouTube Music engine uses it) and Windows calls, through JNA.
    implementation("net.java.dev.jna:jna:5.17.0")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
}

/** CI gives the version: 1.<n>.0 (MSI wants three numbers, each in range). */
val jamVersion = (findProperty("jamWinVersion") as String?) ?: "1.0.0"

compose.desktop {
    application {
        mainClass = "app.jam.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Xss4m", "-XX:+UseG1GC", "-Dsun.java2d.d3d=true")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Jam"
            packageVersion = jamVersion
            description = "Jam: Spotify and YouTube Music, with extensions and Discord status"
            vendor = "Jam"
            copyright = "GPL-3.0. Built on Orpheon (formerly Myspoti, github.com/kota-zo/myspoti) by kota-zo."
            licenseFile.set(rootProject.file("../LICENSE"))
            appResourcesRootDir.set(project.layout.projectDirectory.dir("app-resources"))
            includeAllModules = true
            windows {
                iconFile.set(project.file("jam.ico"))
                menu = true
                menuGroup = "Jam"
                shortcut = true
                perUserInstall = true
                dirChooser = false
                upgradeUuid = "6f1b5a3e-7d2c-4b9e-9a51-3c8e2f4d7a10"
            }
        }
    }
}

/** Renders screens off-screen to PNGs (no display needed): `./gradlew :app:shots`. */
tasks.register<JavaExec>("shots") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("app.jam.desktop.ShotsKt")
    args(layout.buildDirectory.dir("shots").get().asFile.absolutePath)
}

/** Development aid (the deps workflow): downloads every jar of the build, for offline builds elsewhere. */
tasks.register("resolveAll") {
    notCompatibleWithConfigurationCache("resolves configurations at execution")
    doLast { listOf("compileClasspath", "runtimeClasspath").forEach { project.configurations.getByName(it).resolve() } }
}

/** Development aid: the run classpath, for starting the app by hand (tests on Linux). */
tasks.register("printClasspath") {
    notCompatibleWithConfigurationCache("reads configurations")
    dependsOn("classes")
    doLast { println("CP=" + (sourceSets.main.get().runtimeClasspath).asPath) }
}
