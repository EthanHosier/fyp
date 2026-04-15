package com.github.ethanhosier.analysis.metrics.pmd

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PmdRunnerTest {

    @Test
    fun `runs PMD on the CK fixture and returns structured violations`() {
        val fixture = Path.of("src/test/resources/ck-fixture")
        val result = PmdRunner().run(fixture)

        println(result)
        assertTrue(
            result.processingErrors.isEmpty(),
            "unexpected processing errors: ${result.processingErrors}",
        )
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

        // Every violation should be relativised to the fixture root.
        result.violations.forEach { v ->
            assertTrue(
                !v.file.startsWith("/"),
                "violation file should be relative, was: ${v.file}",
            )
        }
    }
}
