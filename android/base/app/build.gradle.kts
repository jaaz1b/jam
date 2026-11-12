import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile = rootProject.file("keystore/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// -PautomixLab: a test app with its own data, installed alongside every regular/dev build.
val automixLab = findProperty("automixLab") != null


/**
 * Jam: the ABIs a release has an APK for, and how they are named in it (UpdateChecker reads the same
 * names): Jam-Android-<version>.apk for phones (64-bit ARM), -x86_64 for emulators. These are the
 * ABIs YouTube Music mode's engine is built for (Jam for Android before 3.0 shipped the same two).
 */
val RELEASE_ABIS = listOf("arm64-v8a", "x86_64")
val ABI_NAMES = mapOf("arm64-v8a" to "", "x86_64" to "-x86_64")
/**
 * The first word of a release's APK names (UpdateChecker reads it from BuildConfig.APK_NAME). It
 * follows the app id: Myspoti's own releases (app.myspoti, app.myspoti.stable) are "Myspoti-...",
 * so their update check, which installs only its own package, opens the release page for these
 * instead of trying an APK it cannot install over itself.
 */
val APK_NAME = "Jam-Android"

android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("roborazzi.test.record", "true") }
        }
    }
    namespace = "app.myspoti.engine"
    compileSdk = 37

    // Jam: one app, "app.jam.android". CI gives the version ("3.0.0-build.12", not a beta: no adb
    // command line, release logs stripped). Local builds (no version given) are on the beta side.
    val version = (findProperty("appVersionName") as String?) ?: "1.0.0"
    // "-beta" as in UpdateChecker.isBeta, one predicate for the app id, the label and the log stripping.
    val beta = findProperty("appVersionName") == null || version.contains("-beta")
    defaultConfig {
        // Jam: one channel, Jam for Android's own id, so it installs over Jam 2.x and keeps its data.
        applicationId = if (automixLab) "app.myspoti.automixlab" else "app.jam.android"
        manifestPlaceholders["appLabel"] = if (automixLab) "Myspoti Mix Lab" else "Jam"
        // The adb command line (com.music.spotui.cli): betas and debug builds only.
        manifestPlaceholders["cliEnabled"] = beta.toString()
        minSdk = 29
        targetSdk = 36
        // CI release builds pass the tag's version (-PappVersionName / -PappVersionCode).
        versionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 200
        versionName = version
        // GitHub "owner/repo" whose releases the app checks for updates; empty = off
        // (local builds). CI sets it to the repository it builds.
        // Not in the dev and Mix Lab builds (below): no release carries their app id.
        buildConfigField("String", "UPDATE_REPO", "\"${if (automixLab) "" else (findProperty("updateRepo") as String?) ?: System.getenv("JAM_UPDATE_REPO") ?: ""}\"")
        buildConfigField("String", "APK_NAME", "\"$APK_NAME\"")
        // Jam: the Orpheon release this app is built on (kota-zo/Orpheon@70f323c). Extensions say
        // which Orpheon they need: they are checked against this, not against Jam's own version.
        buildConfigField("String", "ORPHEON_VERSION", "\"1.0.0\"")
        // Jam: -PjamStartupTrace logs start-up timings (app/jam/JamStartup.kt), for the emulator test.
        buildConfigField("boolean", "JAM_STARTUP_TRACE", (findProperty("jamStartupTrace") != null).toString())
        // -Pabis=arm64-v8a: one APK for one phone (the test builds). Without it, the ABI splits below.
        (findProperty("abis") as String?)?.let { ndk { abiFilters += it.split(",") } }
        // On-device tests (codecs): ./gradlew connectedDebugAndroidTest
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Jam's signing (as Jam for Android 2.x, so updates install over it and keep the logins): the
    // private key from the CI secrets (JAM_KEYSTORE_*), else keystore/keystore.properties, else the
    // public key in android/keystore (see its README).
    val envStore = System.getenv("JAM_KEYSTORE_FILE")
    val hasReleaseKey = true
    signingConfigs {
        create("release") {
            if (envStore != null) {
                storeFile = file(envStore)
                storePassword = System.getenv("JAM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("JAM_KEY_ALIAS")
                keyPassword = System.getenv("JAM_KEY_PASSWORD")?.ifEmpty { null } ?: System.getenv("JAM_KEYSTORE_PASSWORD")
            } else if (!keystorePropertiesFile.exists()) {
                storeFile = rootProject.file("../keystore/jam-debug.jks")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            } else {
                storeFile = rootProject.file("keystore/${keystoreProperties.getProperty("storeFile")}")
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to the release apps (own data, own sign-in).
            if (!automixLab) applicationIdSuffix = ".dev"
            manifestPlaceholders["appLabel"] = if (automixLab) "Myspoti Mix Lab" else "Jam dev"
            manifestPlaceholders["cliEnabled"] = "true"
            buildConfigField("String", "UPDATE_REPO", "\"\"")
        }
        release {
            // Real key when provided (CI releases); otherwise the debug key, so a local
            // release build installs over the debug one while testing.
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
            // -PoverDev: the optimised build under the dev app's id, to measure the speed of a
            // beta on the test phone without replacing the signed beta installed there.
            if (findProperty("overDev") != null && !automixLab) {
                applicationIdSuffix = ".dev"
                manifestPlaceholders["appLabel"] = "Jam dev"
                buildConfigField("String", "UPDATE_REPO", "\"\"")
            }
            // The Mix Lab stays drivable from the CLI in its optimised build, as in its debug one.
            if (automixLab) manifestPlaceholders["cliEnabled"] = "true"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Betas are release builds too, but their log reports read Log.v/d/i.
            if (!beta) proguardFiles("proguard-strip-logs.pro")
        }
    }
    // One build, every APK a release carries (named by collectApks below): one per ABI, and the
    // universal one (both ABIs). x86 is left out: no phone or TV Myspoti targets runs it.
    splits {
        abi {
            isEnable = findProperty("abis") == null
            reset()
            include(*RELEASE_ABIS.toTypedArray())
            isUniversalApk = false
        }
    }
    // Jam: arm64 phones and x86_64 emulators (the ABIs above). YouTube Music mode's libmpv and the
    // ONNX runtime both bring the NDK's C++ runtime: one copy.
    packaging { jniLibs { excludes += listOf("lib/x86/**", "lib/armeabi-v7a/**"); pickFirsts += "**/libc++_shared.so" } }
    // Jam: native libraries compressed in the APK, as Jam for Android had them: YouTube Music mode's
    // engine, libmpv and FFmpeg stored as they are made the APK about 100 MB bigger. Extracted
    // once at install; loading them costs the same.
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":host-android"))
    // Jam: YouTube Music mode (Jam for Android's Tauri app, ytm/build.gradle.kts) and its Discord presence.
    implementation(project(":ytm"))
    // YouTube Music as the account provider (listen sync, library read, metadata on the user's session).
    implementation(project(":api-ytmusic"))
    // The native engine: the account's audio outside the Web Player page (NativeAudio), behind its setting.
    implementation(project(":engine-android"))
    // The watch's app asks this one for the account's session and playlists (WearLinkService).
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    // AutoMix: song analysis and transition planning (pure Kotlin), Beat This on ONNX Runtime.
    implementation(project(":automix"))
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")
    // Third-party extensions: the sandbox (Rhino), manifests, packages and catalogues.
    implementation(project(":extensions"))
    // Metadata (search, albums, artists, ISRC) from several providers in the user's order, with signed script updates.
    implementation(project(":catalog"))
    // Passing the setup to another of the user's devices by QR code, over the local network.
    implementation(project(":pass"))
    // The queue's drag and drop (Apache-2.0): the row carried stays in the list, auto-scroll at the edges.
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    // Jam: scan the invite QR code (Google code scanner, no camera permission).
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    // Myspoti TV: the support links as QR codes (Apache-2.0), the encoder alone.
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
    // Virtual time for the tests of timed coroutines (graces, timeouts): no real waits.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.11.0")
    // The real org.json in unit tests (android.jar only has stubs).
    testImplementation("org.json:json:20240303")
    // A real XML pull parser in unit tests (the TTML lyrics parser; the tests skip themselves without it).
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    // Roborazzi asks for kotlinx-io 0.3.3; Ktor (removed) had raised it to 0.9.1, the version the unit tests ran with.
    testImplementation("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-io-bytestring:0.9.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.media)


    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.material)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Native UI (ported from Spotui, GPL-3.0)
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("com.github.bumptech.glide:compose:1.0.0-beta10")
    // Used directly (Glide, GlideUrl, the disk cache...): beta10 only brings it at runtime.
    implementation("com.github.bumptech.glide:glide:5.0.9")
    implementation("androidx.palette:palette-ktx:1.0.0")
    // Frosted (blurred) bars of the Immersive look. Apache-2.0.
    implementation("dev.chrisbanes.haze:haze:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Awaiting the watch link's tasks (Data Layer channels).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    implementation(libs.serialization.json)
    // Looping Spotify Canvas video on the now-playing screen
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.8.0")
    // HLS (.m3u8) Live Radio streams, which most big broadcasters use.
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    // Installs the Baseline Profiles shipped by Compose & co. on sideloaded builds
    // (faster start, less jank; Play does this itself for store installs).
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // YouTube music videos (search + streams), as in Spotui / NewPipe.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation(libs.okhttp)
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-datasource:1.8.0")
    implementation("androidx.media3:media3-database:1.8.0")

    // Screenshots of the TV screens on the computer (TvScreenshotTest): Robolectric draws them, Roborazzi saves them.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.40.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.40.1")
    testImplementation(platform(libs.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")





}
/**
 * `./gradlew :app:collectApks` after assembleRelease: the APKs under clear names in
 * build/release-apks/: Jam-Android-3.0.0-build.12.apk (64-bit ARM phones and TVs) and
 * Jam-Android-3.0.0-build.12-x86_64.apk (emulators). Local builds are Jam-Android-Beta-..., the
 * optimised dev builds Jam-Android-dev-...
 */
tasks.register("collectApks") {
    mustRunAfter("assembleRelease")
    val version = (findProperty("appVersionName") as String?) ?: "1.0.0"
    val beta = findProperty("appVersionName") == null || version.contains("-beta")
    val app = when { findProperty("overDev") != null -> "$APK_NAME-dev"; beta -> "$APK_NAME-Beta"; else -> APK_NAME }
    // Copies: the configuration cache cannot keep a reference to the script's own values.
    val abis = RELEASE_ABIS.toList()
    val abiNames = ABI_NAMES.toMap()
    val from = layout.buildDirectory.dir("outputs/apk/release")
    val into = layout.buildDirectory.dir("release-apks")
    doLast {
        val src = from.get().asFile
        val dst = into.get().asFile.apply { deleteRecursively(); mkdirs() }
        fun copy(name: String, to: String) {
            val f = src.resolve(name)
            if (!f.exists()) throw GradleException("$name missing: run assembleRelease without -Pabis first")
            f.copyTo(dst.resolve(to), overwrite = true)
        }
        // Jam: Jam-Android-<version>.apk (phones), Jam-Android-<version>-x86_64.apk (emulators).
        abis.forEach { abi ->
            copy("app-$abi-release.apk", "$app-$version${abiNames.getValue(abi)}.apk")
        }
        dst.listFiles()?.sorted()?.forEach { println("${it.name}  ${it.length() / 1024 / 1024} MB") }
    }
}
