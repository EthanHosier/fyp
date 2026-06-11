package com.github.ethanhosier.analysis.refactoring

import java.nio.file.Files
import java.nio.file.Path

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
