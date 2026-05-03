package com.github.ethanhosier.analysis.metrics.cpd

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CpdRunnerTest {

    @Test
    fun `attaches a snippet to every duplication occurrence`() {
        val fixture = Path.of("src/test/resources/cpd-fixture")
        // Lower the token threshold so the small fixture clones register;
        // production default (50) wouldn't trip on them.
        val result = CpdRunner(minimumTokens = 20).run(fixture)

        assertTrue(
            result.processingErrors.isEmpty(),
            "unexpected processing errors: ${result.processingErrors}",
        )
        assertTrue(
            result.duplications.isNotEmpty(),
            "expected at least one duplication group from the fixture clones",
        )

        for (dup in result.duplications) {
            assertTrue(dup.occurrences.size >= 2, "every group should have ≥2 occurrences")
            for (occ in dup.occurrences) {
                val snippet = occ.snippet
                assertNotNull(
                    snippet,
                    "expected snippet for occurrence ${occ.file}:${occ.beginLine}-${occ.endLine}",
                )
                // Mini unified-diff shape: file header, hunk header, every
                // body line a context line. Mirrors the assertions in
                // PmdRunnerTest so both snippet sources stay aligned.
                assertTrue(
                    snippet.patch.startsWith("diff --git a/${occ.file} b/${occ.file}\n"),
                    "snippet should open with a `diff --git` header for the occurrence's file; got:\n${snippet.patch}",
                )
                assertTrue(
                    snippet.patch.contains("\n@@ -"),
                    "snippet should contain a hunk header; got:\n${snippet.patch}",
                )
                val bodyLines = snippet.patch.lineSequence()
                    .dropWhile { !it.startsWith("@@") }
                    .drop(1)
                    .filter { it.isNotEmpty() }
                    .toList()
                assertTrue(
                    bodyLines.all { it.startsWith(" ") },
                    "every snippet body line should start with a context-marker space; got:\n$bodyLines",
                )
                // Both fixture clones contain this distinctive line.
                assertTrue(
                    bodyLines.any { it.contains("total += x") },
                    "snippet body should include the cloned source; got:\n$bodyLines",
                )
            }
        }
    }
}
