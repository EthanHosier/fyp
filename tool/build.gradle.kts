plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.intelliJPlatform) apply false
    alias(libs.plugins.changelog) apply false
    alias(libs.plugins.qodana) apply false
    alias(libs.plugins.kover) apply false
}

tasks.wrapper {
    gradleVersion = providers.gradleProperty("gradleVersion").get()
}
