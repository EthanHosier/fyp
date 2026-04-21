package com.github.ethanhosier.analysis.jdt

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertTrue

/**
 * End-to-end proof: boot Equinox, install our refactoring bundle,
 * invoke the Extract Method entry point, observe rewritten source.
 *
 * The refactoring code lives in `:refactoring-bundle` so when we
 * reflectively call `JdtRefactorer.extractMethod(...)` via the bundle's
 * classloader, the `IWorkspace`/`ICompilationUnit`/etc. classes it
 * references are the same ones the Eclipse bundles registered — no
 * classloader split.
 */
class JdtExtractMethodSpike {

    @Test
    fun `extract method through refactoring bundle`(@TempDir tmp: Path) {
        val framework = EquinoxBootstrap.start(tmp.resolve("osgi"))
        try {
            val bundleJar = Path.of(
                System.getProperty("refactoring.bundle.jar")
                    ?: error("refactoring.bundle.jar sys prop not set"),
            )
            val ctx = framework.bundleContext
            val bundle = ctx.installBundle("reference:" + bundleJar.toUri())
            bundle.start(0)

            val refactorerClass = bundle.loadClass(
                "com.github.ethanhosier.refactoringbundle.JdtRefactorer",
            )
            val instance = refactorerClass.getField("INSTANCE").get(null)
            val method = refactorerClass.getMethod(
                "extractMethod",
                String::class.java, String::class.java, String::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                String::class.java,
            )

            val source = """
                package org.example;

                public class OrderPricingService {
                    public double priceFor(String tier, double total) {
                        if (tier.equals("gold")) {
                            double discount = total * 0.2;
                            double taxed = (total - discount) * 1.2;
                            return taxed - 5.0;
                        }
                        return total;
                    }
                }
            """.trimIndent()

            // Extract the three-statement body inside the `if (gold)` —
            // lines 6..8 in the source above.
            val rewritten = method.invoke(
                instance,
                "spike",
                "src/org/example/OrderPricingService.java",
                source,
                6,
                8,
                "handleGold",
            ) as String

            println("=== rewritten ===\n$rewritten\n=== end ===")
            assertTrue("handleGold" in rewritten, "new method name must appear")
            assertTrue("private" in rewritten, "new method must be private")
        } finally {
            framework.stop()
            // Block until Equinox finishes releasing OSGi storage locks,
            // otherwise JUnit's @TempDir cleanup races the framework and
            // fails to delete the workspace dir.
            framework.waitForStop(5_000)
        }
    }
}
