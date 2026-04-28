plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinSerialization)
}

group = "com.github.ethanhosier.metricslambda"
version = "0.0.1"

kotlin {
    // AWS Lambda's `java21` runtime — must match :metrics-core or the
    // shared bytecode won't load.
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

// Same asm-eviction as :metrics-core, since PMD comes in transitively.
configurations.all {
    exclude(group = "asm", module = "asm")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":metrics-core"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.aws.lambda.core)
    // S3 only — the handler downloads the shadow-repo bundle. The Lambda
    // runtime itself is the invoker, so this module never imports
    // software.amazon.awssdk.lambda.
    implementation(libs.aws.sdk.s3)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
}

tasks.test { useJUnitPlatform() }

/**
 * Fat-jar of the handler + all runtime deps, suitable for `package_type =
 * "Zip"` Lambda deployments. For container-image deployments (planned),
 * the Dockerfile copies this jar into the image.
 *
 * Excludes META-INF signing entries that would otherwise cause
 * `SecurityException` at runtime when the merged jar's signatures don't
 * match the merged class set.
 */
tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Builds a self-contained handler jar with all runtime deps."
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
