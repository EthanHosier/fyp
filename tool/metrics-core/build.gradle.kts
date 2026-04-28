plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinSerialization)
}

group = "com.github.ethanhosier.metricscore"
version = "0.0.1"

kotlin {
    // Java 21 — matches AWS Lambda's `java21` runtime so the bytecode
    // emitted here loads cleanly when :metrics-lambda packages this jar
    // into its container image. :analysis is on 23 (for refactoring-miner)
    // but newer JDKs load older bytecode, so the cross-module classpath
    // is fine.
    jvmToolchain(21)
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
