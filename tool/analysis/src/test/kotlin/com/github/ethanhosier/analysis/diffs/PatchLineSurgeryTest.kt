package com.github.ethanhosier.analysis.diffs

import kotlin.test.Test
import kotlin.test.assertEquals

class PatchLineSurgeryTest {

    @Test
    fun `drops plus run beside a minus run and recomputes header`() {
        // Hunk has one -OLD run at oldLine 11 and one +NEW run at
        // newLine 11. Dropping just the +-run leaves the -run alive,
        // so the hunk survives; new header reflects net oldLen=3,
        // newLen=2 (no +NEW).
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,3 +10,3 @@\n" +
            " a\n" +
            "-OLD\n" +
            "+NEW\n" +
            " c\n"

        val result = PatchLineSurgery.removeRuns(
            patch,
            addedRunStartLines = mapOf("Foo.java" to setOf(11)),
        )

        val expected =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,3 +10,2 @@\n" +
            " a\n" +
            "-OLD\n" +
            " c\n"
        assertEquals(expected, result)
    }

    @Test
    fun `drops minus run beside a plus run and recomputes header`() {
        // Symmetric of the above: drop the -run, keep the +-run.
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,3 +10,3 @@\n" +
            " a\n" +
            "-OLD\n" +
            "+NEW\n" +
            " c\n"

        val result = PatchLineSurgery.removeRuns(
            patch,
            removedRunStartLines = mapOf("Foo.java" to setOf(11)),
        )

        val expected =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,2 +10,3 @@\n" +
            " a\n" +
            "+NEW\n" +
            " c\n"
        assertEquals(expected, result)
    }

    @Test
    fun `drops the only run and collapses the hunk to context-only -- hunk elided`() {
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,3 +10,4 @@\n" +
            " a\n" +
            "+x\n" +
            " b\n" +
            " c\n" +
            "@@ -20,3 +21,3 @@\n" +
            " p\n" +
            "-q\n" +
            "+Q\n" +
            " r\n"

        val result = PatchLineSurgery.removeRuns(
            patch,
            addedRunStartLines = mapOf("Foo.java" to setOf(11)),
        )

        // First hunk's only +run dropped → context-only → elided.
        // Second hunk preserved verbatim.
        val expected =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -20,3 +21,3 @@\n" +
            " p\n" +
            "-q\n" +
            "+Q\n" +
            " r\n"
        assertEquals(expected, result)
    }

    @Test
    fun `file whose every hunk collapses is elided entirely`() {
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,1 @@\n" +
            "+x\n"

        val result = PatchLineSurgery.removeRuns(
            patch,
            addedRunStartLines = mapOf("Foo.java" to setOf(4)),
        )
        assertEquals("", result)
    }

    @Test
    fun `other files in patch untouched by single-file surgery`() {
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,1 @@\n" +
            "+x\n" +
            "diff --git a/Bar.java b/Bar.java\n" +
            "--- a/Bar.java\n" +
            "+++ b/Bar.java\n" +
            "@@ -3,0 +4,1 @@\n" +
            "+y\n"

        val result = PatchLineSurgery.removeRuns(
            patch,
            addedRunStartLines = mapOf("Foo.java" to setOf(4)),
        )

        val expected =
            "diff --git a/Bar.java b/Bar.java\n" +
            "--- a/Bar.java\n" +
            "+++ b/Bar.java\n" +
            "@@ -3,0 +4,1 @@\n" +
            "+y\n"
        assertEquals(expected, result)
    }

    @Test
    fun `nonexistent run is silently skipped`() {
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,1 @@\n" +
            "+x\n"

        val result = PatchLineSurgery.removeRuns(
            patch,
            addedRunStartLines = mapOf("Foo.java" to setOf(999)),
        )
        assertEquals(patch, result)
    }

    @Test
    fun `empty surgery roundtrips patch unchanged`() {
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,3 +10,3 @@\n" +
            " a\n" +
            "-x\n" +
            "+X\n" +
            " b\n"

        assertEquals(patch, PatchLineSurgery.removeRuns(patch))
    }

    @Test
    fun `multi-line plus run dropped as a unit by its startLine`() {
        // +x and +y form one contiguous +-run starting at newLine 11.
        // Targeting startLine 11 drops both.
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,4 +10,6 @@\n" +
            " a\n" +
            "+x\n" +
            "+y\n" +
            "-z\n" +
            " b\n" +
            " c\n"

        val result = PatchLineSurgery.removeRuns(
            patch,
            addedRunStartLines = mapOf("Foo.java" to setOf(11)),
        )

        val expected =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,4 +10,3 @@\n" +
            " a\n" +
            "-z\n" +
            " b\n" +
            " c\n"
        assertEquals(expected, result)
    }

    @Test
    fun `drops both minus and plus runs in same replace hunk`() {
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,4 +10,4 @@\n" +
            " ctx1\n" +
            "-a\n" +
            "-b\n" +
            "+A\n" +
            "+B\n" +
            " ctx2\n"

        val result = PatchLineSurgery.removeRuns(
            patch,
            addedRunStartLines = mapOf("Foo.java" to setOf(11)),
            removedRunStartLines = mapOf("Foo.java" to setOf(11)),
        )
        assertEquals("", result)
    }

    @Test
    fun `section heading suffix preserved in rewritten header`() {
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,3 +10,3 @@ public void foo()\n" +
            " a\n" +
            "-OLD\n" +
            "+NEW\n" +
            " c\n"

        val result = PatchLineSurgery.removeRuns(
            patch,
            addedRunStartLines = mapOf("Foo.java" to setOf(11)),
        )

        val expected =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,3 +10,2 @@ public void foo()\n" +
            " a\n" +
            "-OLD\n" +
            " c\n"
        assertEquals(expected, result)
    }

    @Test
    fun `two separate plus runs in same hunk each targetable`() {
        // Context line between +x and +y → two distinct +-runs at
        // newLines 11 and 13. Dropping both still leaves only context
        // → hunk elided.
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,3 +10,5 @@\n" +
            " a\n" +
            "+x\n" +
            " b\n" +
            "+y\n" +
            " c\n"

        val result = PatchLineSurgery.removeRuns(
            patch,
            addedRunStartLines = mapOf("Foo.java" to setOf(11, 13)),
        )
        assertEquals("", result)
    }

    @Test
    fun `modification collapses to pure insertion -- oldStart shifts back by one`() {
        // Original: replace 17 old lines with 1 new line at position 31.
        // Drop the -run → pure insertion of 1 line. Conventional header
        // for "insert at position 31" is `@@ -30,0 +31,1 @@`, not
        // `@@ -31,0 +31,1 @@` (which git apply would reject as the
        // wrong insertion boundary).
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -31,17 +31,1 @@\n" +
            "-l1\n" +
            "-l2\n" +
            "-l3\n" +
            "-l4\n" +
            "-l5\n" +
            "-l6\n" +
            "-l7\n" +
            "-l8\n" +
            "-l9\n" +
            "-l10\n" +
            "-l11\n" +
            "-l12\n" +
            "-l13\n" +
            "-l14\n" +
            "-l15\n" +
            "-l16\n" +
            "-l17\n" +
            "+blank\n"

        val result = PatchLineSurgery.removeRuns(
            patch,
            removedRunStartLines = mapOf("Foo.java" to setOf(31)),
        )

        val expected =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -30,0 +31,1 @@\n" +
            "+blank\n"
        assertEquals(expected, result)
    }

    @Test
    fun `modification collapses to pure deletion -- newStart shifts back by one`() {
        // Original: replace 1 old line at position 10 with 5 new lines.
        // Drop the +run → pure deletion of old line 10. Conventional
        // header for "delete old line 10 in a result that has no new
        // line at position 10": `@@ -10,1 +9,0 @@`.
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,1 +10,5 @@\n" +
            "-gone\n" +
            "+a\n" +
            "+b\n" +
            "+c\n" +
            "+d\n" +
            "+e\n"

        val result = PatchLineSurgery.removeRuns(
            patch,
            addedRunStartLines = mapOf("Foo.java" to setOf(10)),
        )

        val expected =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,1 +9,0 @@\n" +
            "-gone\n"
        assertEquals(expected, result)
    }

    @Test
    fun `pure-insertion or pure-deletion start does not shift if already pure`() {
        // Original is already a pure insertion (oldLen=0). Dropping
        // nothing → header unchanged.
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,1 @@\n" +
            "+x\n"
        assertEquals(patch, PatchLineSurgery.removeRuns(patch))
    }

    @Test
    fun `dropping only one of two plus runs leaves the other and survives`() {
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,3 +10,5 @@\n" +
            " a\n" +
            "+x\n" +
            " b\n" +
            "+y\n" +
            " c\n"

        val result = PatchLineSurgery.removeRuns(
            patch,
            addedRunStartLines = mapOf("Foo.java" to setOf(11)),
        )

        val expected =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,3 +10,4 @@\n" +
            " a\n" +
            " b\n" +
            "+y\n" +
            " c\n"
        assertEquals(expected, result)
    }
}
