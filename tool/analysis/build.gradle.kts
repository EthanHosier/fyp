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
    implementation(libs.kxs.ts.gen.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.ktor.server.test.host)

    // JDT extract-method spike — test-only, scoped to `jdt` package.
    // Scaled up as missing-class errors surface; start minimal.
    testImplementation("org.eclipse.jdt:org.eclipse.jdt.core:3.38.0")
    testImplementation("org.eclipse.jdt:org.eclipse.jdt.core.manipulation:1.23.0") {
        // JDT manipulation transitively drags JFace/SWT for UI bits we
        // don't touch; SWT's platform fragment uses an unresolved Gradle
        // variable (${osgi.platform}) and fails to resolve.
        exclude(group = "org.eclipse.platform", module = "org.eclipse.swt")
    }
    testImplementation("org.eclipse.platform:org.eclipse.core.runtime:3.31.100")
    testImplementation("org.eclipse.platform:org.eclipse.core.resources:3.21.0")
    testImplementation("org.eclipse.platform:org.eclipse.text:3.14.0")
    testImplementation("org.eclipse.platform:org.eclipse.jface.text:3.25.0") {
        exclude(group = "org.eclipse.platform", module = "org.eclipse.swt")
        exclude(group = "org.eclipse.platform", module = "org.eclipse.jface")
    }
    testImplementation("org.eclipse.platform:org.eclipse.ltk.core.refactoring:3.14.0")
    // Declarative Services runtime — Eclipse platform bundles register
    // their services via DS components, so they stay unresolved until a
    // DS container is present.
    testImplementation("org.apache.felix:org.apache.felix.scr:2.2.12")
    // OSGi compendium APIs Felix SCR + Eclipse bundles import.
    testImplementation("org.osgi:org.osgi.service.component:1.5.1")
    testImplementation("org.osgi:org.osgi.util.function:1.2.0")
    testImplementation("org.osgi:org.osgi.util.promise:1.3.0")
    testImplementation("org.osgi:org.osgi.util.tracker:1.5.4")
    // SWT's Maven POM uses an unresolved `${osgi.platform}` variable
    // that fails to resolve transitively. Explicitly depend on the
    // platform-specific artifact that actually contains the classes,
    // then let Gradle upgrade the transitive `org.eclipse.swt` stub to
    // match. Only the native matching the host is needed.
    testImplementation("org.eclipse.platform:org.eclipse.swt.cocoa.macosx.aarch64:3.130.0") {
        exclude(group = "org.eclipse.platform", module = "org.eclipse.swt.\${osgi.platform}")
    }
}

// Dedicated resolvable configuration that pulls the refactoring bundle
// jar from `:refactoring-bundle`. Consuming via a configuration (not
// direct cross-project task access) keeps Gradle's configuration cache
// happy — the value becomes a file-collection input the test task can
// depend on without reaching into another project at config time.
val refactoringBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    refactoringBundle(project(":refactoring-bundle"))
}

tasks.test {
    useJUnitPlatform()
    val bundleFiles: FileCollection = refactoringBundle
    inputs.files(bundleFiles)
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf("-Drefactoring.bundle.jar=" + bundleFiles.singleFile.absolutePath)
        },
    )
}

tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Runs the analysis HTTP server entrypoint."
    mainClass.set("com.github.ethanhosier.analysis.server.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// Emits TS types for the dashboard by walking AnalysisReport's kotlinx
// serial descriptor. Output lives in the dashboard module's src/generated/
// (gitignored) and `:ide-plugin:buildDashboard` depends on this task so
// Vite always compiles against fresh types derived from the Kotlin model.
val dashboardTypesOutput = rootProject.layout.projectDirectory
    .file("dashboard/src/generated/report-types.ts")

tasks.register<JavaExec>("generateDashboardTypes") {
    group = "build"
    description = "Generates TypeScript types for the React dashboard from AnalysisReport."
    mainClass.set("com.github.ethanhosier.analysis.codegen.GenerateDashboardTypesKt")
    classpath = sourceSets["main"].runtimeClasspath
    args(dashboardTypesOutput.asFile.absolutePath)

    inputs.dir(layout.projectDirectory.dir("src/main/kotlin/com/github/ethanhosier/analysis/metrics/model"))
    inputs.dir(layout.projectDirectory.dir("src/main/kotlin/com/github/ethanhosier/analysis/metrics/gitdiff"))
    inputs.dir(layout.projectDirectory.dir("src/main/kotlin/com/github/ethanhosier/analysis/miner/model"))
    outputs.file(dashboardTypesOutput)
}
