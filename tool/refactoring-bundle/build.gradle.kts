plugins {
    alias(libs.plugins.kotlin)
}

group = "com.github.ethanhosier.refactoringbundle"
version = "0.0.1"

kotlin {
    jvmToolchain(23)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    // JDT deps are `compileOnly` — at runtime they come from the OSGi
    // bundles already loaded into the embedding framework. Bundling
    // them again would cause classloader-split pain all over again.
    compileOnly("org.eclipse.jdt:org.eclipse.jdt.core:3.38.0")
    compileOnly("org.eclipse.jdt:org.eclipse.jdt.core.manipulation:1.23.0")
    compileOnly("org.eclipse.platform:org.eclipse.core.runtime:3.31.100")
    compileOnly("org.eclipse.platform:org.eclipse.core.resources:3.21.0")
    compileOnly("org.eclipse.platform:org.eclipse.text:3.14.0")
    compileOnly("org.eclipse.platform:org.eclipse.ltk.core.refactoring:3.14.0")
}

// Emit the jar with an OSGi manifest so Equinox treats it as a bundle
// when installed. `Require-Bundle` lists the JDT bits we call into;
// OSGi's classloader then delegates those to the matching bundles
// inside the framework — so the JdtRefactorer's `IWorkspace` is the
// same Class<?> as the one core.resources registers.
tasks.jar {
    manifest {
        attributes(
            "Manifest-Version" to "1.0",
            "Bundle-ManifestVersion" to "2",
            "Bundle-SymbolicName" to "com.github.ethanhosier.refactoringbundle",
            "Bundle-Version" to "0.0.1",
            "Bundle-RequiredExecutionEnvironment" to "JavaSE-17",
            "Require-Bundle" to listOf(
                "org.eclipse.core.resources",
                "org.eclipse.core.runtime",
                "org.eclipse.jdt.core",
                "org.eclipse.jdt.core.manipulation",
                "org.eclipse.text",
                "org.eclipse.ltk.core.refactoring",
            ).joinToString(","),
            "Export-Package" to "com.github.ethanhosier.refactoringbundle",
            // Let OSGi resolve any missing package at load time from
            // whichever bundle exports it — saves us enumerating every
            // Kotlin stdlib + JDT package we happen to touch.
            "DynamicImport-Package" to "*",
        )
    }
}
