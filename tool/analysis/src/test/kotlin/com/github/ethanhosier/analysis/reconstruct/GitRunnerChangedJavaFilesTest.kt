package com.github.ethanhosier.analysis.reconstruct

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitRunnerChangedJavaFilesTest {

    @Test
    fun `between returns added modified deleted java files only`(@TempDir tmp: Path) {
        val git = initRepo(tmp)
        write(tmp, "src/A.java", "package p; class A {}")
        write(tmp, "src/B.java", "package p; class B {}")
        write(tmp, "README.md", "hi")
        git.addAll()
        val sha1 = git.commit("init")

        // Modify A, delete B, add C, change README (non-java).
        write(tmp, "src/A.java", "package p; class A { int x = 1; }")
        Files.delete(tmp.resolve("src/B.java"))
        write(tmp, "src/C.java", "package p; class C {}")
        write(tmp, "README.md", "bye")
        git.addAll()
        val sha2 = git.commit("change")

        val changed = git.changedJavaFilesBetween(sha1, sha2)
        assertEquals(setOf("src/A.java", "src/B.java", "src/C.java"), changed)
    }

    @Test
    fun `between surfaces both sides of a rename`(@TempDir tmp: Path) {
        val git = initRepo(tmp)
        write(tmp, "src/Old.java", "package p; class Old { int aLongFieldName = 1; }")
        git.addAll()
        val sha1 = git.commit("init")

        Files.delete(tmp.resolve("src/Old.java"))
        write(tmp, "src/New.java", "package p; class New { int aLongFieldName = 1; }")
        git.addAll()
        val sha2 = git.commit("rename")

        val changed = git.changedJavaFilesBetween(sha1, sha2)
        assertTrue("src/Old.java" in changed, "old path missing: $changed")
        assertTrue("src/New.java" in changed, "new path missing: $changed")
    }

    @Test
    fun `from head dirty picks up modified added deleted untracked java`(@TempDir tmp: Path) {
        val git = initRepo(tmp)
        write(tmp, "src/A.java", "package p; class A {}")
        write(tmp, "src/B.java", "package p; class B {}")
        git.addAll()
        git.commit("init")

        // Modify A, delete B (unstaged), add untracked C.java, untracked non-java.
        write(tmp, "src/A.java", "package p; class A { int x = 1; }")
        Files.delete(tmp.resolve("src/B.java"))
        write(tmp, "src/C.java", "package p; class C {}")
        write(tmp, "notes.md", "hi")

        val dirty = git.changedJavaFilesFromHeadDirty()
        assertEquals(setOf("src/A.java", "src/B.java", "src/C.java"), dirty)
    }

    private fun initRepo(dir: Path): GitRunner {
        val git = GitRunner(dir)
        git.init()
        git.setLocalIdentity("test@local", "Test")
        return git
    }

    private fun write(root: Path, rel: String, content: String) {
        val p = root.resolve(rel)
        Files.createDirectories(p.parent ?: root)
        Files.writeString(p, content)
    }
}
