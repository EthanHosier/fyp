package com.github.ethanhosier.analysis.metrics.pmd

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PmdRunnerTest {

    @Test
    fun `runs PMD on the CK fixture and returns violations plus metrics`() {
        val fixture = Path.of("src/test/resources/ck-fixture")
        val result = PmdRunner().run(fixture)

        println(result)
        assertTrue(
            result.processingErrors.isEmpty(),
            "unexpected processing errors: ${result.processingErrors}",
        )

        // --- violations ---
        assertTrue(
            result.violations.isNotEmpty(),
            "expected at least one violation on Greeter.java with default rulesets",
        )

        // Greeter catches NullPointerException in `safe()`, which the
        // errorprone ruleset flags as AvoidCatchingNPE. This anchors the
        // test to a rule that won't be retitled lightly.
        val npe = result.violations.firstOrNull { it.rule == "AvoidCatchingNPE" }
        assertNotNull(
            npe,
            "expected AvoidCatchingNPE violation; got rules: ${result.violations.map { it.rule }}",
        )
        assertEquals("src/main/java/sample/Greeter.java", npe.file)
        assertTrue(npe.priority in 1..5, "priority out of range: ${npe.priority}")
        assertTrue(npe.ruleSet.isNotEmpty(), "ruleSet should be populated")

        result.violations.forEach { v ->
            assertTrue(
                !v.file.startsWith("/"),
                "violation file should be relative, was: ${v.file}",
            )
        }

        // --- class metrics ---
        assertEquals(1, result.classMetrics.size)
        val greeter = result.classMetrics.single()
        assertEquals("sample.Greeter", greeter.className)
        assertEquals("src/main/java/sample/Greeter.java", greeter.file)
        assertTrue(greeter.ncss > 0, "ncss should be > 0, was ${greeter.ncss}")
        assertTrue(greeter.woc in 0.0..1.0, "woc out of range: ${greeter.woc}")
        // Greeter has one pure accessor (getCallCount) so noam should be >= 1.
        assertTrue(greeter.noam >= 1, "noam should be >= 1, was ${greeter.noam}")

        // --- method metrics ---
        // Constructor + 4 methods = 5 executables.
        assertEquals(5, result.methodMetrics.size)
        result.methodMetrics.forEach { m ->
            assertEquals("sample.Greeter", m.className)
            assertTrue(m.cyclo >= 1, "cyclo should be >= 1 for ${m.signature}")
            assertTrue(m.cognitive >= 0, "cognitive should be >= 0 for ${m.signature}")
            assertTrue(
                m.npath.toBigInteger() >= 1.toBigInteger(),
                "npath should be >= 1 for ${m.signature}, was ${m.npath}",
            )
        }

        // `sum(int[])` has a for-each loop — expect cyclo >= 2.
        val sum = result.methodMetrics.single { it.signature.startsWith("sum(") }
        assertTrue(sum.cyclo >= 2, "sum should have cyclo >= 2, was ${sum.cyclo}")

        // `safe(String)` has a try/catch — cognitive complexity should be > 0.
        val safe = result.methodMetrics.single { it.signature.startsWith("safe(") }
        assertTrue(safe.cognitive >= 1, "safe should have cognitive >= 1, was ${safe.cognitive}")
    }
}
