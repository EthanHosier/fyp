package com.github.ethanhosier.analysis.diffs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatchFilterTest {

    @Test
    fun `blank patch returns empty`() {
        assertEquals("", PatchFilter.filter("", emptyMap(), emptyMap()))
    }

    @Test
    fun `empty ranges drop every hunk`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -1,3 +1,3 @@
             one
            -two
            +TWO
             three
        """.trimIndent() + "\n"
        assertEquals("", PatchFilter.filter(patch, emptyMap(), emptyMap()))
    }

    @Test
    fun `hunk kept when right range hits added line`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -1,3 +1,3 @@
             one
            -two
            +TWO
             three
        """.trimIndent() + "\n"
        val result = PatchFilter.filter(
            patch = patch,
            leftRanges = emptyMap(),
            rightRanges = mapOf("Foo.java" to listOf(2..2)),
        )
        assertTrue(result.contains("+TWO"), "result: $result")
        assertTrue(result.contains("-two"))
        assertTrue(result.contains("diff --git a/Foo.java"))
    }

    @Test
    fun `hunk kept when left range hits removed line`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -10,3 +10,3 @@
             keep
            -old
            +new
             keep
        """.trimIndent() + "\n"
        val result = PatchFilter.filter(
            patch = patch,
            leftRanges = mapOf("Foo.java" to listOf(11..11)),
            rightRanges = emptyMap(),
        )
        assertTrue(result.contains("-old"))
    }

    @Test
    fun `context line in range does not keep hunk`() {
        // Range targets line 1 (which is a context line ` one`), not the
        // changed line 2. Hunk must be dropped.
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -1,3 +1,3 @@
             one
            -two
            +TWO
             three
        """.trimIndent() + "\n"
        val result = PatchFilter.filter(
            patch = patch,
            leftRanges = mapOf("Foo.java" to listOf(1..1)),
            rightRanges = mapOf("Foo.java" to listOf(1..1)),
        )
        assertEquals("", result)
    }

    @Test
    fun `multiple hunks filtered independently`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -5,3 +5,3 @@
             a
            -b
            +B
             c
            @@ -20,3 +20,3 @@
             x
            -y
            +Y
             z
        """.trimIndent() + "\n"
        val result = PatchFilter.filter(
            patch = patch,
            leftRanges = emptyMap(),
            rightRanges = mapOf("Foo.java" to listOf(21..21)),
        )
        assertTrue(result.contains("+Y"))
        assertTrue(!result.contains("+B"), "first hunk should be dropped: $result")
    }

    @Test
    fun `file entry dropped when no hunk matches`() {
        val patch = """
            diff --git a/Keep.java b/Keep.java
            --- a/Keep.java
            +++ b/Keep.java
            @@ -1,1 +1,1 @@
            -a
            +A
            diff --git a/Drop.java b/Drop.java
            --- a/Drop.java
            +++ b/Drop.java
            @@ -1,1 +1,1 @@
            -x
            +X
        """.trimIndent() + "\n"
        val result = PatchFilter.filter(
            patch = patch,
            leftRanges = emptyMap(),
            rightRanges = mapOf("Keep.java" to listOf(1..1)),
        )
        assertTrue(result.contains("diff --git a/Keep.java"))
        assertTrue(!result.contains("Drop.java"), "Drop.java should be absent: $result")
    }

    @Test
    fun `rename header preserved alongside kept hunk`() {
        val patch = """
            diff --git a/Old.java b/New.java
            similarity index 80%
            rename from Old.java
            rename to New.java
            --- a/Old.java
            +++ b/New.java
            @@ -1,3 +1,3 @@
             x
            -y
            +Y
             z
        """.trimIndent() + "\n"
        val result = PatchFilter.filter(
            patch = patch,
            leftRanges = mapOf("Old.java" to listOf(2..2)),
            rightRanges = emptyMap(),
        )
        assertTrue(result.contains("rename from Old.java"))
        assertTrue(result.contains("rename to New.java"))
        assertTrue(result.contains("+Y"))
    }
}
