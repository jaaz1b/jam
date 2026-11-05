plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: a private key from CI secrets if configured, otherwise the public debug key in
// android/keystore (see its README). Either way the signature stays the same between builds.
val ksFile = System.getenv("JAM_KEYSTORE_FILE")

android {
    namespace = "app.jam"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.jam.android"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            if (ksFile != null) {
                storeFile = file(ksFile)
                storePassword = System.getenv("JAM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("JAM_KEY_ALIAS")
                keyPassword = System.getenv("JAM_KEY_PASSWORD")?.ifEmpty { null } ?: System.getenv("JAM_KEYSTORE_PASSWORD")
            } else {
                storeFile = rootProject.file("keystore/jam-debug.jks")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        getByName("debug") {
            storeFile = rootProject.file("keystore/jam-debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
