plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "com.github.ethanhosier.analysis"
version = "0.0.1"

kotlin {
    // Bumped from 21 to 23 so Gradle resolves refactoring-miner's thin
    // `runtimeElements` variant (declared jvm.version=23) instead of its
    // `shadowRuntimeElements` fat jar — the fat jar bundles a 2008-era
    // asm `SignatureVisitor` as an interface, which collides at runtime
    // with PMD 7.13's modern asm 9.8 (where it's an abstract class).
    jvmToolchain(23)
}

application {
    mainClass.set("com.github.ethanhosier.analysis.cli.MainKt")
}

repositories {
    mavenCentral()
}

// Exclude the ancient `asm:asm` coordinate that refactoring-miner drags
// in transitively (via rendersnake → guice 3.0 → sisu-cglib). It ships
// the same `org.objectweb.asm.*` classnames as the modern
// `org.ow2.asm:asm:9.8` that PMD depends on — whichever loads first
// wins, and the ancient one breaks PMD with `IncompatibleClassChangeError`
// on `SignatureVisitor` (interface vs abstract class).
configurations.all {
    exclude(group = "asm", module = "asm")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":shared"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ck)
    implementation(libs.pmd.java)
    implementation(libs.refactoring.miner)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.ktor.server.test.host)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Runs the analysis HTTP server entrypoint."
    mainClass.set("com.github.ethanhosier.analysis.server.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
}
