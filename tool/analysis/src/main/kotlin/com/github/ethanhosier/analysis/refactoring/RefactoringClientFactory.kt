package com.github.ethanhosier.analysis.refactoring

import java.nio.file.Files
import java.nio.file.Path

/**
 * Builds a [RefactoringClient] by booting the embedded Equinox
 * framework and installing the refactoring bundle. Expensive (~2–5s
 * cold) — call once per process and reuse the returned client across
 * all requests.
 *
 * The bundle jar's location is resolved via
 * `-Drefactoring.bundle.jar=<path>` (set by Gradle for `runServer` and
 * tests). No fallback yet; packaged distributions will need to set
 * the property themselves.
 */
object RefactoringClientFactory {

    fun create(dataArea: Path): RefactoringClient {
        val framework = EquinoxBootstrap.start(dataArea)
        val bundleJar = resolveBundleJar()
        val bundle = framework.bundleContext
            .installBundle("reference:" + bundleJar.toUri())
        bundle.start(0)
        val cls = bundle.loadClass("com.github.ethanhosier.refactoringbundle.JdtRefactorer")
        val instance = cls.getField("INSTANCE").get(null)
        return RefactoringClient(framework, instance, cls)
    }

    private fun resolveBundleJar(): Path {
        val raw = System.getProperty("refactoring.bundle.jar")
            ?: error(
                "refactoring.bundle.jar system property not set; the build " +
                    "should be providing it via refactoringBundle configuration",
            )
        val path = Path.of(raw)
        require(Files.isRegularFile(path)) { "refactoring bundle jar not found at $path" }
        return path
    }
}
