plugins {
    id("com.android.application")
}

android {
    namespace = "app.jam.presence"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.jam.presence"
        minSdk = 29
        targetSdk = 36
        versionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("appVersionName") as String?) ?: "1.0.0-dev"
    }

    // Signed like Jam: the private key from the CI secrets (JAM_KEYSTORE_*), else the public key in
    // android/keystore, so every build installs over the last one.
    val envStore = System.getenv("JAM_KEYSTORE_FILE")
    signingConfigs {
        create("jam") {
            if (envStore != null) {
                storeFile = file(envStore)
                storePassword = System.getenv("JAM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("JAM_KEY_ALIAS")
                keyPassword = System.getenv("JAM_KEY_PASSWORD")?.ifEmpty { null } ?: System.getenv("JAM_KEYSTORE_PASSWORD")
            } else {
                storeFile = rootProject.file("../keystore/jam-debug.jks")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("jam")
        }
        debug {
            signingConfig = signingConfigs.getByName("jam")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    packaging { resources { excludes += setOf("META-INF/*.version", "META-INF/*.kotlin_module", "kotlin/**", "DebugProbesKt.bin") } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
}
