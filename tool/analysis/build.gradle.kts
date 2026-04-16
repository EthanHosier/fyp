plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "com.github.ethanhosier.analysis"
version = "0.0.1"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.github.ethanhosier.analysis.cli.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":shared"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ck)
    implementation(libs.pmd.java)
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
