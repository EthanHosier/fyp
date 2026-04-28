plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinSerialization)
}

group = "com.github.ethanhosier.metricscore"
version = "0.0.1"

kotlin {
    // Match :analysis (23) so refactoring-miner's runtimeElements variant
    // resolves consistently if this module ever ends up on a classpath
    // that shares it. CK + PMD work fine on 23.
    jvmToolchain(23)
}

repositories {
    mavenCentral()
}

// PMD's transitive deps drag in the ancient `asm:asm` coordinate that
// shadows the modern `org.ow2.asm:asm:9.8` PMD itself uses; same fix as
// :analysis. Keep them in sync if either is bumped.
configurations.all {
    exclude(group = "asm", module = "asm")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ck)
    implementation(libs.pmd.java)
}
