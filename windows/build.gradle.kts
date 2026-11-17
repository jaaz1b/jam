plugins {
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// One JDK for the whole Windows build (21, the one jpackage bundles): Orpheon's modules ask for
// 17 in their own build files, which this build doesn't need to keep.
subprojects {
    afterEvaluate {
        extensions.findByType<org.jetbrains.kotlin.gradle.dsl.KotlinTopLevelExtension>()?.jvmToolchain(21)
    }
}
