package com.github.ethanhosier.analysis.diffs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnifiedDiffParserTest {

    @Test
    fun `blank patch parses to no files`() {
        assertEquals(0, UnifiedDiffParser.parse("").files.size)
        assertEquals(0, UnifiedDiffParser.parse("   \n  \n").files.size)
    }

    @Test
    fun `single file single hunk parses header and body`() {
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

        val parsed = UnifiedDiffParser.parse(patch)
        assertEquals(1, parsed.files.size)
        val file = parsed.files[0]
        assertEquals("Foo.java", file.oldPath)
        assertEquals("Foo.java", file.newPath)
        assertEquals(
            listOf(
                "diff --git a/Foo.java b/Foo.java",
                "--- a/Foo.java",
                "+++ b/Foo.java",
            ),
            file.headerLines,
        )
        assertEquals(1, file.hunks.size)
        val hunk = file.hunks[0]
        assertEquals("@@ -1,3 +1,3 @@", hunk.header)
        assertEquals(1, hunk.oldStart)
        assertEquals(3, hunk.oldLen)
        assertEquals(1, hunk.newStart)
        assertEquals(3, hunk.newLen)
        assertEquals(
            listOf(" one", "-two", "+TWO", " three", ""),
            hunk.body,
        )
    }

    @Test
    fun `hunk header without length defaults to 1`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -10 +10,2 @@
             ctx
            +new
        """.trimIndent() + "\n"

        val hunk = UnifiedDiffParser.parse(patch).files.single().hunks.single()
        assertEquals(10, hunk.oldStart)
        assertEquals(1, hunk.oldLen)
        assertEquals(10, hunk.newStart)
        assertEquals(2, hunk.newLen)
    }

    @Test
    fun `pure insertion hunk has oldLen 0`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -5,0 +6,2 @@
            +x
            +y
        """.trimIndent() + "\n"

        val hunk = UnifiedDiffParser.parse(patch).files.single().hunks.single()
        assertEquals(5, hunk.oldStart)
        assertEquals(0, hunk.oldLen)
        assertEquals(6, hunk.newStart)
        assertEquals(2, hunk.newLen)
    }

    @Test
    fun `hunk header with section heading still parses`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -10,3 +10,3 @@ public void foo()
             a
            -b
            +B
             c
        """.trimIndent() + "\n"

        val hunk = UnifiedDiffParser.parse(patch).files.single().hunks.single()
        assertEquals("@@ -10,3 +10,3 @@ public void foo()", hunk.header)
        assertEquals(10, hunk.oldStart)
        assertEquals(3, hunk.oldLen)
    }

    @Test
    fun `multiple hunks split correctly`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -1,3 +1,3 @@
             a
            -b
            +B
             c
            @@ -20,2 +20,3 @@
             d
            +e
             f
        """.trimIndent() + "\n"

        val hunks = UnifiedDiffParser.parse(patch).files.single().hunks
        assertEquals(2, hunks.size)
        assertEquals(1, hunks[0].oldStart)
        assertEquals(20, hunks[1].oldStart)
        assertEquals(listOf(" a", "-b", "+B", " c"), hunks[0].body)
    }

    @Test
    fun `multiple files split correctly`() {
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
            @@ -1,1 +1,1 @@
            -OLD
            +NEW
        """.trimIndent() + "\n"

        val files = UnifiedDiffParser.parse(patch).files
        assertEquals(2, files.size)
        assertEquals("Foo.java", files[0].newPath)
        assertEquals("Bar.java", files[1].newPath)
        assertEquals(1, files[0].hunks.size)
        assertEquals(1, files[1].hunks.size)
    }

    @Test
    fun `created file has null oldPath`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            new file mode 100644
            --- /dev/null
            +++ b/Foo.java
            @@ -0,0 +1,2 @@
            +a
            +b
        """.trimIndent() + "\n"

        val file = UnifiedDiffParser.parse(patch).files.single()
        assertNull(file.oldPath)
        assertEquals("Foo.java", file.newPath)
    }

    @Test
    fun `deleted file has null newPath`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            deleted file mode 100644
            --- a/Foo.java
            +++ /dev/null
            @@ -1,2 +0,0 @@
            -a
            -b
        """.trimIndent() + "\n"

        val file = UnifiedDiffParser.parse(patch).files.single()
        assertEquals("Foo.java", file.oldPath)
        assertNull(file.newPath)
    }

    @Test
    fun `file with no hunks (pure rename) parsed with empty hunks list`() {
        val patch = """
            diff --git a/Old.java b/New.java
            similarity index 100%
            rename from Old.java
            rename to New.java
        """.trimIndent() + "\n"

        val file = UnifiedDiffParser.parse(patch).files.single()
        assertEquals(0, file.hunks.size)
        assertEquals(
            listOf("rename from Old.java", "rename to New.java"),
            file.headerLines.filter { it.startsWith("rename ") },
        )
    }

    @Test
    fun `preamble before first diff git is skipped`() {
        val patch = """
            commit abcdef
            Author: someone

            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -1,1 +1,1 @@
            -a
            +b
        """.trimIndent() + "\n"

        val parsed = UnifiedDiffParser.parse(patch)
        assertEquals(1, parsed.files.size)
        assertEquals("diff --git a/Foo.java b/Foo.java", parsed.files[0].headerLines[0])
    }

    @Test
    fun `malformed hunk header throws`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ garbage @@
             a
        """.trimIndent() + "\n"

        val ex = runCatching { UnifiedDiffParser.parse(patch) }.exceptionOrNull()
        assertEquals(true, ex != null)
    }
}
