// Emulator test only (never released): a stand-in "Apple Music" with Apple Music's package name,
// whose media session plays a known song, so the smoke test can check Jam Presence end to end.
plugins {
    id("com.android.application")
}

android {
    namespace = "app.jam.presence.fakeplayer"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.apple.android.music"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "test"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
