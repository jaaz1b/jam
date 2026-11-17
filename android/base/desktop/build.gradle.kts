// Jam for Windows: the Jam app for the PC. A Compose window in Jam's look over Orpheon's Spotify API
// (:api-spotify), which drives the Spotify Web Player in a hidden Microsoft Edge (or Chrome) through
// the DevTools protocol (:host-cdp). YouTube Music mode starts Jam's YouTube Music engine (jam-ytm.exe,
// built by .github/workflows/jam-windows.yml next to this app).
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":host-cdp"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.coroutines.swing)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
}

/** CI gives the version: 1.0.<run number> (MSI wants three numbers, each in range). */
val jamVersion = (findProperty("jamWinVersion") as String?) ?: "1.0.0"

compose.desktop {
    application {
        mainClass = "app.jam.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Xss2m")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Jam"
            packageVersion = jamVersion
            description = "Jam: Spotify and YouTube Music, with Discord status"
            vendor = "Jam"
            copyright = "GPL-3.0. Spotify mode is Orpheon by kota-zo."
            licenseFile.set(rootProject.file("../../LICENSE"))
            // jam-ytm.exe and its files, copied here by CI (YouTube Music mode); empty locally.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("app-resources"))
            modules("java.net.http", "jdk.crypto.ec", "java.naming", "jdk.unsupported", "java.management")
            windows {
                iconFile.set(project.file("jam.ico"))
                menu = true
                menuGroup = "Jam"
                shortcut = true
                perUserInstall = true
                dirChooser = false
                upgradeUuid = "6f1b5a3e-7d2c-4b9e-9a51-3c8e2f4d7a10"
            }
            linux {
                iconFile.set(project.file("src/main/resources/jam.png"))
            }
        }
    }
}
