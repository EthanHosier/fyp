package com.github.ethanhosier.analysis.alternative.validate

import com.github.ethanhosier.analysis.alternative.validate.RefactoringStepValidator.Status
import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.miner.model.DetectedRefactoring
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("slow")
class RefactoringStepValidatorTest {

    @Test
    fun `untyped spec classified as untyped`(@TempDir tmp: Path) {
        val (pool, shadowGit, _) = setup(tmp, beforeContent = SRC_A_V1, afterContent = SRC_A_V1)
        try {
            val v = RefactoringStepValidator(
                applySpec = { _, _ -> error("should not apply") },
                pool = pool, shadowGit = shadowGit,
            )
            val r = v.validate(listOf(makeStep(spec = null))).single()
            assertEquals(Status.UNTYPED, r.status)
            assertTrue(r.reason!!.startsWith("no typed RefactoringSpec"), "got: ${r.reason}")

            val r2 = v.validate(listOf(makeStep(spec = RefactoringSpec.Other))).single()
            assertEquals(Status.UNTYPED, r2.status)
            assertTrue(r2.reason!!.startsWith("RefactoringSpec.Other"), "got: ${r2.reason}")
        } finally { pool.close() }
    }

    @Test
    fun `dispatcher failed classified as refactor failed with reason`(@TempDir tmp: Path) {
        val (pool, shadowGit, ctx) = setup(tmp, beforeContent = SRC_A_V1, afterContent = SRC_A_V2)
        try {
            val v = RefactoringStepValidator(
                applySpec = { _, _ -> SpecDispatcher.Result.Failed("anchor not found") },
                pool = pool, shadowGit = shadowGit,
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
                pool = pool, shadowGit = shadowGit,
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
                pool = pool, shadowGit = shadowGit,
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
                pool = pool, shadowGit = shadowGit,
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
                pool = pool, shadowGit = shadowGit,
            )
            val r = v.validate(listOf(makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha))).single()
            assertEquals(Status.AST_DIVERGED, r.status)
            assertEquals("file-content mismatch", r.reason)
            assertEquals(listOf("src/A.java"), r.divergedFiles)
        } finally { pool.close() }
    }

    // --- multi-step bracket cases -----------------------------------

    @Test
    fun `two steps sharing bracket both VALID when combined apply matches`(@TempDir tmp: Path) {
        val (pool, shadowGit, ctx) = setup(tmp, beforeContent = SRC_A_V1, afterContent = SRC_A_V2)
        try {
            // Each apply nudges A.java one step closer; together
            // they reproduce the user's V2.
            val v = RefactoringStepValidator(
                applySpec = { _, worktree ->
                    val cur = Files.readString(worktree.resolve("src/A.java"))
                    val next = when (cur) {
                        SRC_A_V1 -> SRC_A_INTERMEDIATE
                        SRC_A_INTERMEDIATE -> SRC_A_V2
                        else -> cur
                    }
                    Files.writeString(worktree.resolve("src/A.java"), next)
                    SpecDispatcher.Result.Ok
                },
                pool = pool, shadowGit = shadowGit,
            )
            val r = v.validate(
                listOf(
                    makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha, stepIndex = 0),
                    makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha, stepIndex = 1),
                ),
            )
            assertEquals(2, r.size)
            assertEquals(Status.VALID, r[0].status, "step 0: ${r[0]}")
            assertEquals(Status.VALID, r[1].status, "step 1: ${r[1]}")
            assertNull(r[0].reason)
            assertNull(r[1].reason)
        } finally { pool.close() }
    }

    @Test
    fun `two steps sharing bracket both AST_DIVERGED when combined apply diverges`(@TempDir tmp: Path) {
        val (pool, shadowGit, ctx) = setup(tmp, beforeContent = SRC_A_V1, afterContent = SRC_A_V2)
        try {
            // Two applies, but the final state is V_OURS — not V2 the user produced.
            val v = RefactoringStepValidator(
                applySpec = { _, worktree ->
                    val cur = Files.readString(worktree.resolve("src/A.java"))
                    val next = when (cur) {
                        SRC_A_V1 -> SRC_A_INTERMEDIATE
                        SRC_A_INTERMEDIATE -> SRC_A_V_OURS
                        else -> cur
                    }
                    Files.writeString(worktree.resolve("src/A.java"), next)
                    SpecDispatcher.Result.Ok
                },
                pool = pool, shadowGit = shadowGit,
            )
            val r = v.validate(
                listOf(
                    makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha, stepIndex = 0),
                    makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha, stepIndex = 1),
                ),
            )
            assertEquals(Status.AST_DIVERGED, r[0].status)
            assertEquals(Status.AST_DIVERGED, r[1].status)
            assertEquals("file-content mismatch", r[0].reason)
            assertEquals("file-content mismatch", r[1].reason)
            assertEquals(listOf("src/A.java"), r[0].divergedFiles)
            assertEquals(listOf("src/A.java"), r[1].divergedFiles)
        } finally { pool.close() }
    }

    @Test
    fun `two steps sharing bracket first apply fails both REFACTOR_FAILED`(@TempDir tmp: Path) {
        val (pool, shadowGit, ctx) = setup(tmp, beforeContent = SRC_A_V1, afterContent = SRC_A_V2)
        try {
            val v = RefactoringStepValidator(
                applySpec = { _, _ -> SpecDispatcher.Result.Failed("boom") },
                pool = pool, shadowGit = shadowGit,
            )
            val r = v.validate(
                listOf(
                    makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha, stepIndex = 0),
                    makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha, stepIndex = 1),
                ),
            )
            assertEquals(Status.REFACTOR_FAILED, r[0].status)
            assertEquals(Status.REFACTOR_FAILED, r[1].status)
            assertNotNull(r[0].reason)
            assertTrue("step #0" in r[0].reason!!, "expected step #0 referenced; got: ${r[0].reason}")
            assertEquals(r[0].reason, r[1].reason)
        } finally { pool.close() }
    }

    @Test
    fun `two steps sharing bracket second apply fails both REFACTOR_FAILED`(@TempDir tmp: Path) {
        val (pool, shadowGit, ctx) = setup(tmp, beforeContent = SRC_A_V1, afterContent = SRC_A_V2)
        try {
            var calls = 0
            val v = RefactoringStepValidator(
                applySpec = { _, worktree ->
                    calls++
                    if (calls == 1) {
                        Files.writeString(worktree.resolve("src/A.java"), SRC_A_INTERMEDIATE)
                        SpecDispatcher.Result.Ok
                    } else {
                        SpecDispatcher.Result.Failed("second-step boom")
                    }
                },
                pool = pool, shadowGit = shadowGit,
            )
            val r = v.validate(
                listOf(
                    makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha, stepIndex = 0),
                    makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha, stepIndex = 1),
                ),
            )
            assertEquals(Status.REFACTOR_FAILED, r[0].status)
            assertEquals(Status.REFACTOR_FAILED, r[1].status)
            assertTrue("step #1" in r[0].reason!!, "expected step #1 referenced; got: ${r[0].reason}")
            assertTrue("second-step boom" in r[0].reason!!)
        } finally { pool.close() }
    }

    @Test
    fun `untyped spec in otherwise typed bracket short-circuits bracket to UNTYPED`(@TempDir tmp: Path) {
        val (pool, shadowGit, ctx) = setup(tmp, beforeContent = SRC_A_V1, afterContent = SRC_A_V2)
        try {
            val v = RefactoringStepValidator(
                applySpec = { _, _ -> error("should not apply: bracket has untyped sibling") },
                pool = pool, shadowGit = shadowGit,
            )
            val r = v.validate(
                listOf(
                    makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha, stepIndex = 0),
                    makeStep(spec = RefactoringSpec.Other, fromSha = ctx.fromSha, toSha = ctx.toSha, stepIndex = 1),
                ),
            )
            assertEquals(Status.UNTYPED, r[0].status)
            assertEquals(Status.UNTYPED, r[1].status)
            assertTrue("step #1" in r[0].reason!!, "expected step #1 referenced; got: ${r[0].reason}")
            assertEquals(r[0].reason, r[1].reason)
        } finally { pool.close() }
    }

    @Test
    fun `independent brackets are validated separately`(@TempDir tmp: Path) {
        val (pool, shadowGit, ctx) = setup(tmp, beforeContent = SRC_A_V1, afterContent = SRC_A_V2)
        try {
            val v = RefactoringStepValidator(
                applySpec = { _, worktree ->
                    Files.writeString(worktree.resolve("src/A.java"), SRC_A_V2)
                    SpecDispatcher.Result.Ok
                },
                pool = pool, shadowGit = shadowGit,
            )
            val r = v.validate(
                listOf(
                    makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha, stepIndex = 0),
                ),
            )
            // Singleton bracket regression: classification works
            // exactly as before.
            assertEquals(1, r.size)
            assertEquals(Status.VALID, r[0].status)
            assertEquals(0, r[0].stepIndex)
        } finally { pool.close() }
    }

    @Test
    fun `multi-step bracket invokes runInSession once around the apply loop`(@TempDir tmp: Path) {
        val (pool, shadowGit, ctx) = setup(tmp, beforeContent = SRC_A_V1, afterContent = SRC_A_V2)
        try {
            var sessionEntries = 0
            var inSession = false
            val applySawSession = mutableListOf<Boolean>()
            val v = RefactoringStepValidator(
                applySpec = { _, worktree ->
                    applySawSession += inSession
                    val cur = Files.readString(worktree.resolve("src/A.java"))
                    val next = when (cur) {
                        SRC_A_V1 -> SRC_A_INTERMEDIATE
                        SRC_A_INTERMEDIATE -> SRC_A_V2
                        else -> cur
                    }
                    Files.writeString(worktree.resolve("src/A.java"), next)
                    SpecDispatcher.Result.Ok
                },
                pool = pool,
                shadowGit = shadowGit,
                runInSession = { body ->
                    sessionEntries++
                    inSession = true
                    try {
                        body()
                    } finally {
                        inSession = false
                    }
                },
            )
            v.validate(
                listOf(
                    makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha, stepIndex = 0),
                    makeStep(spec = SAMPLE_SPEC, fromSha = ctx.fromSha, toSha = ctx.toSha, stepIndex = 1),
                ),
            )
            assertEquals(1, sessionEntries, "runInSession should fire exactly once per bracket")
            assertEquals(listOf(true, true), applySawSession, "every applySpec call must run inside the session")
        } finally { pool.close() }
    }

    @Test
    fun `validate runs one runInSession across all brackets`(@TempDir tmp: Path) {
        val multi = setupMultiBracket(tmp, contents = listOf(SRC_A_V1, SRC_A_V2, SRC_A_INTERMEDIATE, SRC_A_V_OURS))
        try {
            var sessionEntries = 0
            val v = RefactoringStepValidator(
                applySpec = { _, _ -> SpecDispatcher.Result.Failed("ignored") },
                pool = multi.pool,
                shadowGit = multi.git,
                runInSession = { body -> sessionEntries++; body() },
            )
            v.validate(
                listOf(
                    makeStep(spec = SAMPLE_SPEC, fromSha = multi.shas[0], toSha = multi.shas[1], stepIndex = 0),
                    makeStep(spec = SAMPLE_SPEC, fromSha = multi.shas[1], toSha = multi.shas[2], stepIndex = 1),
                    makeStep(spec = SAMPLE_SPEC, fromSha = multi.shas[2], toSha = multi.shas[3], stepIndex = 2),
                ),
            )
            assertEquals(1, sessionEntries, "exactly one session for the whole validate call")
        } finally { multi.pool.close() }
    }

    @Test
    fun `validate resets worktree between brackets`(@TempDir tmp: Path) {
        // Bracket 0 fromSha=c0 (A=v1), bracket 1 fromSha=c1 (A=v2).
        // Recorder reads A.java at apply time.
        val multi = setupMultiBracket(tmp, contents = listOf(SRC_A_V1, SRC_A_V2, SRC_A_INTERMEDIATE))
        try {
            val seen = mutableListOf<String>()
            val v = RefactoringStepValidator(
                applySpec = { _, worktree ->
                    seen += Files.readString(worktree.resolve("src/A.java"))
                    SpecDispatcher.Result.Failed("ignored")
                },
                pool = multi.pool,
                shadowGit = multi.git,
            )
            v.validate(
                listOf(
                    makeStep(spec = SAMPLE_SPEC, fromSha = multi.shas[0], toSha = multi.shas[1], stepIndex = 0),
                    makeStep(spec = SAMPLE_SPEC, fromSha = multi.shas[1], toSha = multi.shas[2], stepIndex = 1),
                ),
            )
            assertEquals(listOf(SRC_A_V1, SRC_A_V2), seen, "second bracket should see the post-reset state")
        } finally { multi.pool.close() }
    }

    @Test
    fun `validate calls refreshProject between brackets`(@TempDir tmp: Path) {
        val multi = setupMultiBracket(tmp, contents = listOf(SRC_A_V1, SRC_A_V2, SRC_A_INTERMEDIATE, SRC_A_V_OURS))
        try {
            var refreshes = 0
            val v = RefactoringStepValidator(
                applySpec = { _, _ -> SpecDispatcher.Result.Failed("ignored") },
                pool = multi.pool,
                shadowGit = multi.git,
                refreshProject = { refreshes++ },
            )
            v.validate(
                listOf(
                    makeStep(spec = SAMPLE_SPEC, fromSha = multi.shas[0], toSha = multi.shas[1], stepIndex = 0),
                    makeStep(spec = SAMPLE_SPEC, fromSha = multi.shas[1], toSha = multi.shas[2], stepIndex = 1),
                    makeStep(spec = SAMPLE_SPEC, fromSha = multi.shas[2], toSha = multi.shas[3], stepIndex = 2),
                ),
            )
            assertEquals(2, refreshes, "refreshProject should fire (brackets - 1) times")
        } finally { multi.pool.close() }
    }

    @Test
    fun `validate does not borrow a second worktree across brackets`(@TempDir tmp: Path) {
        val multi = setupMultiBracket(
            tmp,
            contents = listOf(SRC_A_V1, SRC_A_V2, SRC_A_INTERMEDIATE),
            poolSize = 1,
        )
        try {
            val v = RefactoringStepValidator(
                applySpec = { _, _ -> SpecDispatcher.Result.Failed("ignored") },
                pool = multi.pool,
                shadowGit = multi.git,
            )
            // Just needs to return without hanging.
            val r = v.validate(
                listOf(
                    makeStep(spec = SAMPLE_SPEC, fromSha = multi.shas[0], toSha = multi.shas[1], stepIndex = 0),
                    makeStep(spec = SAMPLE_SPEC, fromSha = multi.shas[1], toSha = multi.shas[2], stepIndex = 1),
                ),
            )
            assertEquals(2, r.size)
        } finally { multi.pool.close() }
    }

    // --- shared fixtures ---------------------------------------------

    private val SRC_A_V1 = "package p; public class A { public int run() { return 1; } }"
    private val SRC_A_V2 = "package p; public class A { public int run() { return 2; } }"
    private val SRC_A_V_OURS = "package p; public class A { public int run() { return 99; } }"

    private val SRC_A_INTERMEDIATE = "package p; public class A { public int run() { return 1 + 1; } }"

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

    private data class MultiCtx(val pool: WorktreePool, val git: GitRunner, val shas: List<String>)

    private fun setupMultiBracket(
        tmpRoot: Path,
        contents: List<String>,
        poolSize: Int = 2,
    ): MultiCtx {
        require(contents.size >= 2) { "need at least 2 commits to form a bracket" }
        val repo = tmpRoot.resolve("shadow")
        Files.createDirectories(repo.resolve("src"))
        val git = GitRunner(repo)
        git.init()
        git.setLocalIdentity("test@local", "Test")
        val shas = mutableListOf<String>()
        for ((i, c) in contents.withIndex()) {
            Files.writeString(repo.resolve("src/A.java"), c)
            git.addAll()
            shas += git.commit("c$i")
        }
        val pool = WorktreePool(repo, tmpRoot.resolve("worktrees"), poolSize)
        return MultiCtx(pool, git, shas)
    }

    private fun makeStep(
        spec: RefactoringSpec?,
        fromSha: String = "from",
        toSha: String = "to",
        stepIndex: Int = 0,
    ): RefactoringStep = RefactoringStep(
        stepIndex = stepIndex,
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
