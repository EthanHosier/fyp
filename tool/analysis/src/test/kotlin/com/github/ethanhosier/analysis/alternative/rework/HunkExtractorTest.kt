package com.github.ethanhosier.analysis.alternative.rework

import com.github.ethanhosier.analysis.diffs.UnifiedDiffParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HunkExtractorTest {

    private fun parseSingleHunk(patch: String): UnifiedDiffParser.Hunk =
        UnifiedDiffParser.parse(patch).files.single().hunks.single()

    @Test
    fun `pure addition produces one added run at newStart`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -5,0 +6,2 @@
            +x
            +y
        """.trimIndent() + "\n"

        val runs = HunkExtractor.partitionRuns(parseSingleHunk(patch))
        assertEquals(1, runs.addedRuns.size)
        assertEquals(6, runs.addedRuns[0].startLine)
        assertEquals(listOf("x", "y"), runs.addedRuns[0].lines)
        assertEquals(emptyList(), runs.removedRuns)
    }

    @Test
    fun `pure deletion produces one removed run at oldStart`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -5,2 +4,0 @@
            -a
            -b
        """.trimIndent() + "\n"

        val runs = HunkExtractor.partitionRuns(parseSingleHunk(patch))
        assertEquals(1, runs.removedRuns.size)
        assertEquals(5, runs.removedRuns[0].startLine)
        assertEquals(listOf("a", "b"), runs.removedRuns[0].lines)
        assertEquals(emptyList(), runs.addedRuns)
    }

    @Test
    fun `replace block produces removed run then added run at correct lines`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -10,4 +10,4 @@
             ctx1
            -a
            -b
            +A
            +B
             ctx2
        """.trimIndent() + "\n"

        val runs = HunkExtractor.partitionRuns(parseSingleHunk(patch))
        assertEquals(1, runs.removedRuns.size)
        assertEquals(11, runs.removedRuns[0].startLine)
        assertEquals(listOf("a", "b"), runs.removedRuns[0].lines)

        assertEquals(1, runs.addedRuns.size)
        assertEquals(11, runs.addedRuns[0].startLine)
        assertEquals(listOf("A", "B"), runs.addedRuns[0].lines)
    }

    @Test
    fun `context between additions yields two separate added runs`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -10,3 +10,5 @@
             ctx1
            +x
             ctx2
            +y
             ctx3
        """.trimIndent() + "\n"

        val runs = HunkExtractor.partitionRuns(parseSingleHunk(patch))
        assertEquals(2, runs.addedRuns.size)
        assertEquals(11, runs.addedRuns[0].startLine)
        assertEquals(listOf("x"), runs.addedRuns[0].lines)
        assertEquals(13, runs.addedRuns[1].startLine)
        assertEquals(listOf("y"), runs.addedRuns[1].lines)
    }

    @Test
    fun `context only hunk yields no runs`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -1,3 +1,3 @@
             a
             b
             c
        """.trimIndent() + "\n"

        val runs = HunkExtractor.partitionRuns(parseSingleHunk(patch))
        assertTrue(runs.addedRuns.isEmpty())
        assertTrue(runs.removedRuns.isEmpty())
    }

    @Test
    fun `no newline marker is ignored`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -1,1 +1,1 @@
            -old
            \ No newline at end of file
            +new
            \ No newline at end of file
        """.trimIndent() + "\n"

        val runs = HunkExtractor.partitionRuns(parseSingleHunk(patch))
        assertEquals(listOf("old"), runs.removedRuns.single().lines)
        assertEquals(listOf("new"), runs.addedRuns.single().lines)
    }

    @Test
    fun `extractFromPatch returns one entry per file`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -1,1 +1,1 @@
            -old
            +new
            diff --git a/Bar.java b/Bar.java
            --- a/Bar.java
            +++ b/Bar.java
            @@ -5,0 +6,1 @@
            +added
        """.trimIndent() + "\n"

        val files = HunkExtractor.extractFromPatch(patch)
        assertEquals(2, files.size)

        val foo = files.single { it.newPath == "Foo.java" }
        assertEquals(listOf("old"), foo.hunks.single().removedRuns.single().lines)
        assertEquals(listOf("new"), foo.hunks.single().addedRuns.single().lines)

        val bar = files.single { it.newPath == "Bar.java" }
        assertEquals(listOf("added"), bar.hunks.single().addedRuns.single().lines)
        assertEquals(6, bar.hunks.single().addedRuns.single().startLine)
        assertTrue(bar.hunks.single().removedRuns.isEmpty())
    }

    @Test
    fun `prefix is stripped from body lines`() {
        // Specifically: a body line of "+    return null;" should
        // contribute "    return null;" (leading spaces preserved).
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -3,0 +4,1 @@
            +    return null;
        """.trimIndent() + "\n"

        val runs = HunkExtractor.partitionRuns(parseSingleHunk(patch))
        assertEquals(listOf("    return null;"), runs.addedRuns.single().lines)
    }

    @Test
    fun `oldLine cursor advances over context and removals`() {
        // Remove at line 11, then context, then add — added run should
        // land at newLine 13 (newStart 10 + 1 ctx after the removal).
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -10,4 +10,4 @@
             c1
            -gone
             c2
            +new
        """.trimIndent() + "\n"

        val runs = HunkExtractor.partitionRuns(parseSingleHunk(patch))
        assertEquals(11, runs.removedRuns.single().startLine)
        // newStart=10; ctx1 puts newLine=11; removal doesn't advance
        // newLine; ctx2 puts newLine=12; the `+new` lands at 12.
        assertEquals(12, runs.addedRuns.single().startLine)
    }
}
