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

    // Equinox + the Eclipse platform bundles are `implementation` — the
    // analysis server boots an embedded OSGi framework at startup, so
    // these jars must be on the runtime classpath for EquinoxBootstrap
    // to discover, install, and start them.
    implementation("org.eclipse.jdt:org.eclipse.jdt.core:3.38.0")
    implementation("org.eclipse.jdt:org.eclipse.jdt.launching:3.23.0")
    implementation("org.eclipse.jdt:org.eclipse.jdt.core.manipulation:1.23.0") {
        // JDT manipulation transitively drags JFace/SWT for UI bits we
        // don't touch; SWT's platform fragment uses an unresolved Gradle
        // variable (${osgi.platform}) and fails to resolve.
        exclude(group = "org.eclipse.platform", module = "org.eclipse.swt")
    }
    implementation("org.eclipse.platform:org.eclipse.core.runtime:3.31.100")
    implementation("org.eclipse.platform:org.eclipse.core.resources:3.21.0")
    implementation("org.eclipse.platform:org.eclipse.text:3.14.0")
    implementation("org.eclipse.platform:org.eclipse.jface.text:3.25.0") {
        exclude(group = "org.eclipse.platform", module = "org.eclipse.swt")
        exclude(group = "org.eclipse.platform", module = "org.eclipse.jface")
    }
    implementation("org.eclipse.platform:org.eclipse.ltk.core.refactoring:3.14.0")
    // Declarative Services runtime — Eclipse platform bundles register
    // their services via DS components, so they stay unresolved until a
    // DS container is present.
    implementation("org.apache.felix:org.apache.felix.scr:2.2.12")
    // OSGi compendium APIs Felix SCR + Eclipse bundles import.
    implementation("org.osgi:org.osgi.service.component:1.5.1")
    implementation("org.osgi:org.osgi.util.function:1.2.0")
    implementation("org.osgi:org.osgi.util.promise:1.3.0")
    implementation("org.osgi:org.osgi.util.tracker:1.5.4")
    // SWT's Maven POM uses an unresolved `${osgi.platform}` variable
    // that fails to resolve transitively. Explicitly depend on the
    // platform-specific artifact that actually contains the classes,
    // then let Gradle upgrade the transitive `org.eclipse.swt` stub to
    // match. Only the native matching the host is needed.
    implementation("org.eclipse.platform:org.eclipse.swt.cocoa.macosx.aarch64:3.130.0") {
        exclude(group = "org.eclipse.platform", module = "org.eclipse.swt.\${osgi.platform}")
    }

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.ktor.server.test.host)
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
    // Slow tests (real Equinox-bundle boot, real worktree validation —
    // see @Tag("slow") on `RefactoringClientTest`,
    // `RefactoringClientBatchSessionTest`, `RefactoringStepValidatorTest`)
    // are excluded by default. Re-enable with `-PrunSlow` to run the
    // whole suite.
    val runSlow = providers.gradleProperty("runSlow").isPresent
    useJUnitPlatform {
        if (!runSlow) excludeTags("slow")
    }
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
    val bundleFiles: FileCollection = refactoringBundle
    inputs.files(bundleFiles)
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf("-Drefactoring.bundle.jar=" + bundleFiles.singleFile.absolutePath)
        },
    )
}

// `:analysis:run` (auto-created by the `application` plugin) drives the
// CLI; like the test and runServer tasks above, it needs the bundle jar
// resolved from the dedicated configuration so RefactoringClientFactory
// can install it into Equinox.
tasks.named<JavaExec>("run") {
    val bundleFiles: FileCollection = refactoringBundle
    inputs.files(bundleFiles)
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf("-Drefactoring.bundle.jar=" + bundleFiles.singleFile.absolutePath)
        },
    )
}

// Emits TS types for the dashboard by walking AnalysisReport's kotlinx
// serial descriptor. Output lives in the dashboard module's src/generated/
// (gitignored) and `:ide-plugin:buildDashboard` depends on this task so
// Vite always compiles against fresh types derived from the Kotlin model.
val dashboardTypesOutput = rootProject.layout.projectDirectory
    .file("dashboard/src/generated/report-types.ts")

// Phase-A standalone driver: dumps `PhaseAResult` JSON for a session
// folder so Phase B can be re-run cheaply without re-paying for
// reconstruction / metrics / synthesis. Pairs with `:phaseB` below.
// Equinox bundle wiring mirrors `:analysis:run` — Phase A still drives
// `IdeRefactoringsRunner`, which needs `RefactoringClientFactory`.
tasks.register<JavaExec>("phaseA") {
    group = "verification"
    description = "Run Phase A only and dump PhaseAResult JSON."
    mainClass.set("com.github.ethanhosier.analysis.cli.PhaseACli")
    classpath = sourceSets["main"].runtimeClasspath
    val bundleFiles: FileCollection = refactoringBundle
    inputs.files(bundleFiles)
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf("-Drefactoring.bundle.jar=" + bundleFiles.singleFile.absolutePath)
        },
    )
}

// Phase-B cached replay: re-assembles an AnalysisReport from a
// PhaseAResult dump + ScoringConfig JSON. No Equinox needed — Phase B
// is pure in-memory.
tasks.register<JavaExec>("phaseB") {
    group = "verification"
    description = "Re-run Phase B (cheap scoring) against a Phase-A JSON dump."
    mainClass.set("com.github.ethanhosier.analysis.cli.PhaseBCli")
    classpath = sourceSets["main"].runtimeClasspath
}

// Phase-2.2 sensitivity sweep: perturbs each scoring weight by ×0.5 /
// ×1.5 against a corpus of Phase-A dumps and emits a CSV of Kendall-τ +
// top-5 hit-rate vs. the baseline ranking. Pure in-memory like phaseB —
// no Equinox bundle wiring needed.
tasks.register<JavaExec>("sensitivity") {
    group = "verification"
    description = "Sweep scoring weights against a Phase-A corpus and emit a sensitivity CSV."
    mainClass.set("com.github.ethanhosier.analysis.experiment.SensitivityExperiment")
    classpath = sourceSets["main"].runtimeClasspath
}

// Phase-2.3 ablation sweep: cumulatively zeroes process-score weights
// against a corpus of Phase-A dumps and emits a CSV of Kendall-τ +
// top-5 hit-rate vs. the full-production ranking. Pure in-memory like
// :sensitivity — no Equinox bundle wiring needed.
tasks.register<JavaExec>("ablation") {
    group = "verification"
    description = "Ablate scoring weights against a Phase-A corpus and emit an ablation CSV."
    mainClass.set("com.github.ethanhosier.analysis.experiment.AblationExperiment")
    classpath = sourceSets["main"].runtimeClasspath
}

// Phase-2.4 divergence-detection driver: per-row injection manifest →
// detector + baseline hits CSV. Pure in-memory like the other Phase-2
// drivers — no Equinox bundle wiring needed.
tasks.register<JavaExec>("divergence") {
    group = "verification"
    description = "Score the detector + baselines against an injection manifest and emit a CSV."
    mainClass.set("com.github.ethanhosier.analysis.experiment.DivergenceExperiment")
    classpath = sourceSets["main"].runtimeClasspath
}

// User-study helpers: per-session DP counts, per-kind trajectory, and
// inter-rater Cohen's kappa. Pure in-memory; no Equinox bundle wiring.
// `workingDir` is set to the repo root so the scripts' default relative
// paths (`fixtures/user-sessions`, etc.) match the Python originals'.
tasks.register<JavaExec>("userSessionStats") {
    group = "verification"
    description = "Per-session DP count table for user-study sessions (mag > 0 only)."
    mainClass.set("com.github.ethanhosier.analysis.experiment.UserSessionStats")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("perKindTrajectory") {
    group = "verification"
    description = "Per-kind DP trajectory across the six-session arc per participant."
    mainClass.set("com.github.ethanhosier.analysis.experiment.PerKindTrajectory")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("raterKappa") {
    group = "verification"
    description = "Cohen's kappa per kind between two rater manifest CSVs."
    mainClass.set("com.github.ethanhosier.analysis.experiment.RaterKappa")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
}

// Phase-8 agent-comparison helper: render an analysis-report.json's
// divergence points as a markdown summary that can be injected into a
// coding agent's prompt before its next session.
tasks.register<JavaExec>("feedbackForAgent") {
    group = "verification"
    description = "Render an analysis-report.json's divergence points as agent-readable markdown feedback."
    mainClass.set("com.github.ethanhosier.analysis.experiment.FeedbackForAgent")
    classpath = sourceSets["main"].runtimeClasspath
}

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
