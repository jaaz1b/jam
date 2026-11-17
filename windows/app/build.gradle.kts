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
    jvmToolchain(17)
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
val androidOnly: List<String> = file("android-only.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

sourceSets {
    main {
        kotlin.srcDir(androidApp.resolve("src/main/java"))
        kotlin.exclude(androidOnly)
        // The app's res/ (strings in every language, drawables, fonts, raw), read at run time by
        // the resource shim (android/content/res, androidx/compose/ui/res).
        resources.srcDir(layout.buildDirectory.dir("generated/res"))
    }
}

/** Copies the Android app's res/ into the jar (as res/...), for the resource shim. */
val copyRes by tasks.registering(Copy::class) {
    from(androidApp.resolve("src/main/res")) { into("res") }
    into(layout.buildDirectory.dir("generated/res"))
}
/** R: one id per resource, generated from res/ (windows/buildSrc RGen). */
val genR by tasks.registering {
    val res = androidApp.resolve("src/main/res")
    val src = androidApp.resolve("src/main/java")
    val out = layout.buildDirectory.dir("generated/r")
    inputs.dir(res); inputs.dir(src); outputs.dir(out); outputs.dir(layout.buildDirectory.dir("generated/rnames"))
    doLast { RGen.generate(res, src, out.get().asFile) }
}
sourceSets.main {
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
    implementation("org.json:json:20240303")
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
            modules("java.net.http", "jdk.crypto.ec", "java.naming", "jdk.unsupported", "java.management", "java.sql", "java.desktop", "jdk.accessibility")
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
