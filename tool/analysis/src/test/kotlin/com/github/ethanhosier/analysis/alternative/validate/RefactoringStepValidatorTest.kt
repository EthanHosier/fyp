package com.github.ethanhosier.analysis.alternative.validate

import com.github.ethanhosier.analysis.alternative.validate.RefactoringStepValidator.Status
import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.miner.model.DetectedRefactoring
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Validator tests use a real on-disk shadow repo + WorktreePool but
 * a fake `applySpec` lambda. The validator itself doesn't invoke
 * JDT — it only orchestrates apply + git diff + AST hash + compare,
 * so a fake apply that mutates files lets us exercise every branch.
 */
class RefactoringStepValidatorTest {

    @Test
    fun `untyped spec classified as untyped`(@TempDir tmp: Path) {
        val (pool, shadowGit, _) = setup(tmp, beforeContent = SRC_A_V1, afterContent = SRC_A_V1)
        try {
            val v = RefactoringStepValidator(
                applySpec = { _, _ -> error("should not apply") },
                pool = pool, shadowGit = shadowGit, parallelism = 1,
            )
            val r = v.validate(listOf(makeStep(spec = null))).single()
            assertEquals(Status.UNTYPED, r.status)
            assertEquals("no typed RefactoringSpec", r.reason)

            val r2 = v.validate(listOf(makeStep(spec = RefactoringSpec.Other))).single()
            assertEquals(Status.UNTYPED, r2.status)
            assertEquals("RefactoringSpec.Other", r2.reason)
        } finally { pool.close() }
    }

    @Test
    fun `dispatcher failed classified as refactor failed with reason`(@TempDir tmp: Path) {
        val (pool, shadowGit, ctx) = setup(tmp, beforeContent = SRC_A_V1, afterContent = SRC_A_V2)
        try {
            val v = RefactoringStepValidator(
                applySpec = { _, _ -> SpecDispatcher.Result.Failed("anchor not found") },
                pool = pool, shadowGit = shadowGit, parallelism = 1,
            )
            val r = v.validate(listOf(makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha))).single()
            assertEquals(Status.REFACTOR_FAILED, r.status)
            assertEquals("anchor not found", r.reason)
        } finally { pool.close() }
    }

    @Test
    fun `apply produces no textual change classified as refactor failed`(@TempDir tmp: Path) {
        val (pool, shadowGit, ctx) = setup(tmp, beforeContent = SRC_A_V1, afterContent = SRC_A_V2)
        try {
            val v = RefactoringStepValidator(
                applySpec = { _, _ -> SpecDispatcher.Result.Ok }, // touches nothing
                pool = pool, shadowGit = shadowGit, parallelism = 1,
            )
            val r = v.validate(listOf(makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha))).single()
            assertEquals(Status.REFACTOR_FAILED, r.status)
            assertEquals("refactoring produced no textual change", r.reason)
        } finally { pool.close() }
    }

    @Test
    fun `file set mismatch classified as ast diverged`(@TempDir tmp: Path) {
        val (pool, shadowGit, ctx) = setup(tmp, beforeContent = SRC_A_V1, afterContent = SRC_A_V2)
        try {
            // Our "apply" touches a *different* file than the user did.
            val v = RefactoringStepValidator(
                applySpec = { _, worktree ->
                    Files.writeString(worktree.resolve("src/Other.java"), "package p; class Other {}")
                    SpecDispatcher.Result.Ok
                },
                pool = pool, shadowGit = shadowGit, parallelism = 1,
            )
            val r = v.validate(listOf(makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha))).single()
            assertEquals(Status.AST_DIVERGED, r.status)
            assertNotNull(r.reason)
            assertTrue("file-set mismatch" in r.reason, "got reason: ${r.reason}")
            val files = r.divergedFiles ?: emptyList()
            assertTrue(files.any { it.endsWith("Other.java") })
            assertTrue(files.any { it.endsWith("A.java") })
        } finally { pool.close() }
    }

    @Test
    fun `apply matching user produces VALID`(@TempDir tmp: Path) {
        val (pool, shadowGit, ctx) = setup(tmp, beforeContent = SRC_A_V1, afterContent = SRC_A_V2)
        try {
            // Replay the user's exact change.
            val v = RefactoringStepValidator(
                applySpec = { _, worktree ->
                    Files.writeString(worktree.resolve("src/A.java"), SRC_A_V2)
                    SpecDispatcher.Result.Ok
                },
                pool = pool, shadowGit = shadowGit, parallelism = 1,
            )
            val r = v.validate(listOf(makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha))).single()
            assertEquals(Status.VALID, r.status, "reason=${r.reason} divergedFiles=${r.divergedFiles}")
            assertNull(r.reason)
            assertNull(r.divergedFiles)
        } finally { pool.close() }
    }

    @Test
    fun `same file set but different ast classified as ast diverged with files`(@TempDir tmp: Path) {
        val (pool, shadowGit, ctx) = setup(tmp, beforeContent = SRC_A_V1, afterContent = SRC_A_V2)
        try {
            // Same file set, but our content differs from the user's.
            val v = RefactoringStepValidator(
                applySpec = { _, worktree ->
                    Files.writeString(worktree.resolve("src/A.java"), SRC_A_V_OURS)
                    SpecDispatcher.Result.Ok
                },
                pool = pool, shadowGit = shadowGit, parallelism = 1,
            )
            val r = v.validate(listOf(makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha))).single()
            assertEquals(Status.AST_DIVERGED, r.status)
            assertEquals("file-content mismatch", r.reason)
            assertEquals(listOf("src/A.java"), r.divergedFiles)
        } finally { pool.close() }
    }

    // --- shared fixtures ---------------------------------------------

    private val SRC_A_V1 = "package p; public class A { public int run() { return 1; } }"
    private val SRC_A_V2 = "package p; public class A { public int run() { return 2; } }"
    private val SRC_A_V_OURS = "package p; public class A { public int run() { return 99; } }"

    private val SAMPLE_SPEC: RefactoringSpec = RefactoringSpec.RenameClass("p.A", "AA")

    private data class Ctx(val fromSha: String, val toSha: String)

    private fun setup(
        tmpRoot: Path,
        beforeContent: String,
        afterContent: String,
    ): Triple<WorktreePool, GitRunner, Ctx> {
        val repo = tmpRoot.resolve("shadow")
        Files.createDirectories(repo.resolve("src"))
        val git = GitRunner(repo)
        git.init()
        git.setLocalIdentity("test@local", "Test")
        Files.writeString(repo.resolve("src/A.java"), beforeContent)
        git.addAll()
        val fromSha = git.commit("v1")
        Files.writeString(repo.resolve("src/A.java"), afterContent)
        if (beforeContent != afterContent) git.addAll()
        val toSha = if (beforeContent != afterContent) git.commit("v2") else fromSha
        // Reset working tree to the checked-out state of the second commit.
        git.checkoutDetach(toSha)
        val pool = WorktreePool(repo, tmpRoot.resolve("worktrees"), 2)
        return Triple(pool, git, Ctx(fromSha, toSha))
    }

    private fun makeStep(
        spec: RefactoringSpec?,
        fromSha: String = "from",
        toSha: String = "to",
    ): RefactoringStep = RefactoringStep(
        stepIndex = 0,
        fromSha = fromSha,
        toSha = toSha,
        toCheckpointIndex = 0,
        timestamp = 0,
        refactoring = DetectedRefactoring(
            type = "Rename Class", description = "test",
            leftSideLocations = emptyList(), rightSideLocations = emptyList(),
            ideRelevant = true,
        ),
        wasPerformedByIde = false,
        spec = spec,
    )
}
