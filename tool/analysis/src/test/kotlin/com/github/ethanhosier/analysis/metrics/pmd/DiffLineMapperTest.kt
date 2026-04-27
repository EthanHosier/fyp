package com.github.ethanhosier.analysis.metrics.pmd

import com.github.ethanhosier.analysis.metrics.pmd.DiffLineMapper.Mapped
import kotlin.test.Test
import kotlin.test.assertEquals

class DiffLineMapperTest {

    @Test
    fun `identity mapper passes lines through unchanged`() {
        val m = DiffLineMapper.IDENTITY
        assertEquals(Mapped.Translated(1), m.map(1))
        assertEquals(Mapped.Translated(42), m.map(42))
    }

    @Test
    fun `pure deletion shifts lines below by minus one and marks the deleted line gone`() {
        // Deletes the single line at old line 10. Mirrors the "delete one
        // of N consecutive literal-ifs" scenario: surviving siblings shift
        // up, and old line 10 itself reports as Deleted.
        val diff = """
            @@ -10 +9,0 @@
            -    if (x == 0) return;
        """.trimIndent()
        val m = DiffLineMapper.parse(diff)

        assertEquals(Mapped.Translated(9), m.map(9))
        assertEquals(Mapped.Deleted, m.map(10))
        assertEquals(Mapped.Translated(10), m.map(11))
        assertEquals(Mapped.Translated(13), m.map(14))
    }

    @Test
    fun `equal-count hunk pairs lines 1 to 1 in order`() {
        // In-place edit: the heuristic the design relies on — old/new
        // line counts match, so a violation at the modified line keeps
        // its identity at the corresponding new line.
        val diff = """
            @@ -3,5 +3,5 @@
            -    System.out.println("hi");
            +    System.out.println("hello");
        """.trimIndent()
        val m = DiffLineMapper.parse(diff)

        for (i in 3..7) assertEquals(Mapped.Translated(i), m.map(i))
    }

    @Test
    fun `unequal-count hunk reports old lines in the hunk as deleted`() {
        // -3 +1: a real rewrite. Pairing inside the hunk would be a guess,
        // so the mapper conservatively reports each old line as deleted.
        val diff = """
            @@ -10,3 +10,1 @@
            -    if (x == null) {
            -      return;
            -    }
            +    Objects.requireNonNull(x);
        """.trimIndent()
        val m = DiffLineMapper.parse(diff)

        assertEquals(Mapped.Deleted, m.map(10))
        assertEquals(Mapped.Deleted, m.map(11))
        assertEquals(Mapped.Deleted, m.map(12))
        // Line just below the hunk shifts by net delta (-2).
        assertEquals(Mapped.Translated(11), m.map(13))
    }

    @Test
    fun `multiple hunks accumulate their net offsets`() {
        // First hunk inserts 3 lines (net +3 below it); second deletes 1
        // (net -1 below it). A line above both is unchanged, between the
        // two it shifts by +3, after both by +2.
        val diff = """
            @@ -10,2 +10,5 @@
             keep
             keep
            +new1
            +new2
            +new3
            @@ -88,1 +91,0 @@
            -gone
        """.trimIndent()
        val m = DiffLineMapper.parse(diff)

        assertEquals(Mapped.Translated(5), m.map(5))    // before everything
        assertEquals(Mapped.Translated(15), m.map(12))  // between hunks (+3)
        assertEquals(Mapped.Deleted, m.map(88))         // inside second hunk
        assertEquals(Mapped.Translated(91), m.map(89))  // after both (+2)
    }

    @Test
    fun `forFile picks out the right section of a multi-file diff`() {
        val diff = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -10 +9,0 @@
            -    if (x == 0) return;
            diff --git a/Bar.java b/Bar.java
            --- a/Bar.java
            +++ b/Bar.java
            @@ -5,1 +5,1 @@
            -    System.out.println("a");
            +    System.out.println("b");
        """.trimIndent()

        val foo = DiffLineMapper.forFile(diff, "Foo.java")
        assertEquals(Mapped.Deleted, foo.map(10))
        assertEquals(Mapped.Translated(10), foo.map(11))

        // Bar's hunk shouldn't leak into Foo's mapper.
        val bar = DiffLineMapper.forFile(diff, "Bar.java")
        assertEquals(Mapped.Translated(5), bar.map(5))

        // Untouched file → identity.
        val baz = DiffLineMapper.forFile(diff, "Baz.java")
        assertEquals(Mapped.Translated(42), baz.map(42))
    }

    @Test
    fun `range with no comma defaults to count of one`() {
        // `@@ -10 +9,0 @@` is a valid pure-deletion header where the old
        // range has count 1 implicitly. Treat it the same as `-10,1`.
        val diff = """
            @@ -10 +9,0 @@
            -    gone;
        """.trimIndent()
        val m = DiffLineMapper.parse(diff)

        assertEquals(Mapped.Deleted, m.map(10))
        assertEquals(Mapped.Translated(10), m.map(11))
    }
}
