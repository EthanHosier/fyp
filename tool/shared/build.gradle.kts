plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinSerialization)
}

group = "com.github.ethanhosier.fyp"
version = "0.0.1"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.kotlinx.serialization.json)
}
