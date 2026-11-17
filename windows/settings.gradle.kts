// Jam for Windows: Orpheon's app (android/base/app) ported to the PC with Compose Multiplatform.
// Its own Gradle build, without the Android Gradle plugin: the Windows app (:app, in windows/app)
// and Orpheon's plain-JVM modules, used from android/base where they are.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\..*") } }
    }
    versionCatalogs {
        create("libs") { from(files("../android/base/gradle/libs.versions.toml")) }
    }
}

rootProject.name = "JamWindows"
include(":app")
for (m in listOf("api", "api-spotify", "api-web", "api-ytmusic", "engine", "extensions", "catalog", "automix", "pass", "host-cdp")) {
    include(":$m")
    project(":$m").projectDir = file("../android/base/$m")
}
