package com.github.ethanhosier.analysis.alternative.validate

import com.github.ethanhosier.analysis.reconstruct.GitRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
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

    @Test
    fun `method declaration order is canonicalised`(@TempDir tmp: Path) {
        write(
            tmp,
            "A.java",
            "package p; public class C { void a() {} void b() {} }",
        )
        write(
            tmp,
            "B.java",
            "package p; public class C { void b() {} void a() {} }",
        )
        assertEquals(JavaFileAstHasher.hashFile(tmp, "A.java"), JavaFileAstHasher.hashFile(tmp, "B.java"))
    }

    @Test
    fun `overload signatures sort distinctly from one another`(@TempDir tmp: Path) {
        write(
            tmp,
            "A.java",
            "package p; public class C { void f(int x) {} void f(String s) {} void f() {} }",
        )
        write(
            tmp,
            "B.java",
            "package p; public class C { void f() {} void f(String s) {} void f(int x) {} }",
        )
        // Three distinct overloads of `f` rearranged — same set, hash equal.
        assertEquals(JavaFileAstHasher.hashFile(tmp, "A.java"), JavaFileAstHasher.hashFile(tmp, "B.java"))
    }

    @Test
    fun `field declaration order still affects the hash`(@TempDir tmp: Path) {
        write(
            tmp,
            "A.java",
            "package p; public class C { int a = 1; int b = 2; }",
        )
        write(
            tmp,
            "B.java",
            "package p; public class C { int b = 2; int a = 1; }",
        )
        assertNotEquals(JavaFileAstHasher.hashFile(tmp, "A.java"), JavaFileAstHasher.hashFile(tmp, "B.java"))
    }

    @Test
    fun `statement order inside a method still affects the hash`(@TempDir tmp: Path) {
        write(
            tmp,
            "A.java",
            "package p; public class C { int run() { int x = 1; int y = 2; return x; } }",
        )
        write(
            tmp,
            "B.java",
            "package p; public class C { int run() { int y = 2; int x = 1; return x; } }",
        )
        assertNotEquals(JavaFileAstHasher.hashFile(tmp, "A.java"), JavaFileAstHasher.hashFile(tmp, "B.java"))
    }

    @Test
    fun `removing or renaming a method still diverges`(@TempDir tmp: Path) {
        write(
            tmp,
            "A.java",
            "package p; public class C { void a() {} void b() {} }",
        )
        write(
            tmp,
            "B.java",
            "package p; public class C { void a() {} void c() {} }",
        )
        // `b` → `c` — different method set; canonicalisation must not mask this.
        assertNotEquals(JavaFileAstHasher.hashFile(tmp, "A.java"), JavaFileAstHasher.hashFile(tmp, "B.java"))
    }

    @Test
    fun `hashFileAtSha matches hashFile for committed content`(@TempDir tmp: Path) {
        val src = "package p; public class C { public int run() { return 1; } }"
        val git = initRepoWith(tmp, "C.java", src)
        val sha = git.head()
        val viaSha = JavaFileAstHasher.hashFileAtSha(git, sha, "C.java")
        val viaFile = JavaFileAstHasher.hashFile(tmp, "C.java")
        assertNotNull(viaSha)
        assertEquals(viaFile, viaSha)
    }

    @Test
    fun `hashFileAtSha returns null for missing path`(@TempDir tmp: Path) {
        val git = initRepoWith(tmp, "C.java", "package p; public class C {}")
        val sha = git.head()
        assertNull(JavaFileAstHasher.hashFileAtSha(git, sha, "ghost/Nope.java"))
    }

    private fun initRepoWith(root: Path, rel: String, content: String): GitRunner {
        val git = GitRunner(root)
        git.init()
        git.setLocalIdentity("test@example.com", "test")
        write(root, rel, content)
        git.addAll()
        git.commit("seed")
        return git
    }

    private fun write(root: Path, rel: String, content: String) {
        val p = root.resolve(rel)
        Files.createDirectories(p.parent ?: root)
        Files.writeString(p, content)
    }
}
