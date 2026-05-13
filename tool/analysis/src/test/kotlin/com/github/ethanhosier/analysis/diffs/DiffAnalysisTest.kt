package com.github.ethanhosier.analysis.diffs

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiffAnalysisTest {
    @Test fun `whitespace-only addition returns true`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -10,0 +11,2 @@
            +
            +
        """.trimIndent()
        assertTrue(DiffAnalysis.isWhitespaceOnly(patch))
    }

    @Test fun `whitespace-only removal returns true`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -10,2 +9,0 @@
            -
            -
        """.trimIndent()
        assertTrue(DiffAnalysis.isWhitespaceOnly(patch))
    }

    @Test fun `mixed blank add and blank remove returns true`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -30,1 +30,2 @@
            -
            +
            +
        """.trimIndent()
        assertTrue(DiffAnalysis.isWhitespaceOnly(patch))
    }

    @Test fun `non-whitespace addition returns false`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -10,0 +11,1 @@
            +    x = 1;
        """.trimIndent()
        assertFalse(DiffAnalysis.isWhitespaceOnly(patch))
    }

    @Test fun `non-whitespace removal returns false`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            --- a/Foo.java
            +++ b/Foo.java
            @@ -10,1 +9,0 @@
            -    x = 1;
        """.trimIndent()
        assertFalse(DiffAnalysis.isWhitespaceOnly(patch))
    }

    @Test fun `empty patch returns false`() {
        assertFalse(DiffAnalysis.isWhitespaceOnly(""))
    }

    @Test fun `header-only patch returns false`() {
        val patch = """
            diff --git a/Foo.java b/Foo.java
            index 1234567..89abcde 100644
            --- a/Foo.java
            +++ b/Foo.java
        """.trimIndent()
        assertFalse(DiffAnalysis.isWhitespaceOnly(patch))
    }
}
