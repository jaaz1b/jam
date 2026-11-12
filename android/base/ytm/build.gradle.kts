// Jam: YouTube Music mode. The Jam for Android app that was (ytm/src-tauri/gen/android/app: the
// Rust engine and Svelte UI run by Tauri, playing through libmpv), built here as a library of the
// Jam app, so both modes are one APK.
//
// Its sources stay where Tauri generates and builds them. Before this module builds, CI runs
// `tauri android build` in ytm/src-tauri (see .github/workflows/android.yml), which compiles the
// engine into src/main/jniLibs, writes Wry's generated Kotlin (TauriActivity...) and
// tauri.settings.gradle, whose Tauri runtime and plugin sources (in the cargo registry) are compiled
// here too. Locally: `cd ytm/src-tauri && tauri android build --apk --target aarch64` once, then
// `./gradlew :app:assembleRelease` here (android/base).
plugins {
    alias(libs.plugins.android.library)
}

val tauriAndroid: File = rootDir.resolve("../../ytm/src-tauri/gen/android").canonicalFile
val jamApp: File = tauriAndroid.resolve("app")

/** The Tauri runtime and the Tauri plugins with Android code, as `tauri android build` listed them. */
val tauriLibs: List<File> = tauriAndroid.resolve("tauri.settings.gradle").let { f ->
    if (!f.exists()) emptyList()
    else Regex("""projectDir\s*=\s*new\s+File\(\s*['"]([^'"]+)['"]\s*\)""").findAll(f.readText())
        .map { m -> File(m.groupValues[1]).let { if (it.isAbsolute) it else tauriAndroid.resolve(it) }.canonicalFile }
        .filter { it.resolve("src/main/java").isDirectory }
        .toList()
}

android {
    namespace = "app.jam.android"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("proguard-ytm.pro")
        tauriLibs.forEach { lib -> lib.resolve("proguard-rules.pro").takeIf { it.exists() }?.let { consumerProguardFiles(it) } }
        jamApp.resolve("proguard-tauri.pro").takeIf { it.exists() }?.let { consumerProguardFiles(it) }
    }
    buildFeatures {
        buildConfig = true
    }
    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            // AGP 9's built-in Kotlin compiles the `kotlin` directories: the code goes in both.
            val code = listOf(jamApp.resolve("src/main/java")) + tauriLibs.map { it.resolve("src/main/java") } +
                // Tauri's runtime reads app.tauri.BuildConfig; built here, not as its own module.
                file("src/tauri-shim/java")
            code.forEach { java.directories += it.path; kotlin.directories += it.path }
            res.directories += jamApp.resolve("src/main/res").path
            assets.directories += jamApp.resolve("src/main/assets").path
            jniLibs.directories += jamApp.resolve("src/main/jniLibs").path
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Jam for Android's own (ytm/src-tauri/gen/android/app/build.gradle.kts).
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.okhttp)
    // The Tauri runtime's (tauri/mobile/android/build.gradle.kts).
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
}

// Only when this module builds: the other modules' unit tests need no Rust toolchain.
val tauriBuilt = tauriLibs.isNotEmpty() && jamApp.resolve("src/main/java/app/jam/android/generated").isDirectory
tasks.named("preBuild") {
    doFirst {
        if (!tauriBuilt) throw GradleException("YouTube Music mode needs the Tauri build first: cd ytm/src-tauri && tauri android build --apk --target aarch64 (see android/base/ytm/build.gradle.kts)")
    }
}
