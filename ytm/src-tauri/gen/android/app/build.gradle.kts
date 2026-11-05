import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("rust")
}

val tauriProperties = Properties().apply {
    val propFile = file("tauri.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

// Release signing: a private key from CI secrets if configured, otherwise the public debug key in
// android/keystore (see its README). Either way the signature stays the same between builds, so
// updates install over each other and keep your logins.
val ksFile = System.getenv("JAM_KEYSTORE_FILE")
val debugKs = rootProject.file("../../../../android/keystore/jam-debug.jks")

android {
    compileSdk = 36
    namespace = "app.jam.android"
    defaultConfig {
        manifestPlaceholders["usesCleartextTraffic"] = "false"
        applicationId = "app.jam.android"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: tauriProperties.getProperty("tauri.android.versionCode", "1")).toInt() + 100
        versionName = "2.0.0"
    }
    signingConfigs {
        create("release") {
            if (ksFile != null) {
                storeFile = file(ksFile)
                storePassword = System.getenv("JAM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("JAM_KEY_ALIAS")
                keyPassword = System.getenv("JAM_KEY_PASSWORD")?.ifEmpty { null } ?: System.getenv("JAM_KEYSTORE_PASSWORD")
            } else {
                storeFile = debugKs
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        // Not named "debug": the Tauri CLI edits the first `getByName("debug")` block it finds.
        create("jamDebug") {
            storeFile = debugKs
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            isDebuggable = true
            isJniDebuggable = true
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("jamDebug")
            packaging {
                jniLibs.keepDebugSymbols.add("*/arm64-v8a/*.so")
                jniLibs.keepDebugSymbols.add("*/x86_64/*.so")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    packaging {
        // Native libraries stay compressed in the APK: a smaller download, extracted once at install.
        jniLibs.useLegacyPackaging = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

rust {
    rootDirRel = "../../../"
}

dependencies {
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

apply(from = "tauri.build.gradle.kts")
