package com.github.ethanhosier.analysis.alternative.validate

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class JavaFileAstHasherTest {

    @Test
    fun `identical sources produce identical hashes`(@TempDir a: Path, @TempDir b: Path) {
        val src = "package p; public class C { public int run() { return 1; } }"
        write(a, "C.java", src)
        write(b, "C.java", src)
        assertEquals(JavaFileAstHasher.hashFile(a, "C.java"), JavaFileAstHasher.hashFile(b, "C.java"))
    }

    @Test
    fun `comment-only differences do not change the hash`(@TempDir tmp: Path) {
        write(tmp, "A.java", "package p; public class A { public int run() { return 1; } }")
        write(tmp, "B.java", "package p; /* hi */ public class A { /** doc */ public int run() { return 1; } }")
        // Same canonical AST modulo comments.
        assertEquals(JavaFileAstHasher.hashFile(tmp, "A.java"), JavaFileAstHasher.hashFile(tmp, "B.java"))
    }

    @Test
    fun `whitespace-only differences do not change the hash`(@TempDir tmp: Path) {
        write(tmp, "A.java", "package p;public class A{public int run(){return 1;}}")
        write(tmp, "B.java", "package p;\n\npublic class A {\n    public int run() {\n        return 1;\n    }\n}\n")
        assertEquals(JavaFileAstHasher.hashFile(tmp, "A.java"), JavaFileAstHasher.hashFile(tmp, "B.java"))
    }

    @Test
    fun `structural change diverges`(@TempDir tmp: Path) {
        write(tmp, "A.java", "package p; public class A { public int run() { return 1; } }")
        write(tmp, "B.java", "package p; public class A { public int run() { return 2; } }")
        assertNotEquals(JavaFileAstHasher.hashFile(tmp, "A.java"), JavaFileAstHasher.hashFile(tmp, "B.java"))
    }

    @Test
    fun `missing file returns null`(@TempDir tmp: Path) {
        assertNull(JavaFileAstHasher.hashFile(tmp, "ghost/Nope.java"))
    }

    private fun write(root: Path, rel: String, content: String) {
        val p = root.resolve(rel)
        Files.createDirectories(p.parent ?: root)
        Files.writeString(p, content)
    }
}
