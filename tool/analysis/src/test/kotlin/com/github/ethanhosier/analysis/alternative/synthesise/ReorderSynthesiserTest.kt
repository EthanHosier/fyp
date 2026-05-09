package com.github.ethanhosier.analysis.alternative.synthesise

import com.github.ethanhosier.analysis.alternative.validate.RefactoringStepValidator
import com.github.ethanhosier.analysis.alternative.validate.RefactoringStepValidator.Status
import com.github.ethanhosier.analysis.alternative.validate.RefactoringStepValidator.StepValidation
import com.github.ethanhosier.analysis.alternative.validate.SpecDispatcher
import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.miner.model.DetectedRefactoring
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReorderSynthesiserTest {

    /**
     * Window-splitting concerns migrated verbatim from the deleted
     * `ReorderWindowLoggerTest`. Tests construct a synthesiser with
     * fakes that always succeed; assertions only look at splitting
     * summary fields, computed before any synthesis runs.
     */
    @Nested
    inner class WindowSplitting {

        @Test
        fun `treats the whole trace as one window when every step is VALID`() {
            val steps = listOf(
                step(0, "aaa", "bbb", renameClass("com.A", "AA")),
                step(1, "bbb", "ccc", renameClass("com.B", "BB")),
                step(2, "ccc", "ddd", renameClass("com.C", "CC")),
            )
            inSplitContext { ctx ->
                val lines = mutableListOf<String>()
                val summary = ctx.synth.run(steps, allValid(steps)) { lines += it }

                assertEquals(1, summary.totalWindows)
                assertEquals(1, summary.eligibleWindows)
                assertEquals(0, summary.singletonWindows)
                assertEquals(3, summary.typedCount)
                assertEquals(0, summary.untypedCount)
                assertEquals(0, summary.divergentCount)
                assertEquals(0, summary.refactorFailedCount)
                assertTrue(lines.any { "window #0 aaa→ddd" in it && "3 typed specs" in it })
            }
        }

        @Test
        fun `untyped steps split the trace into separate windows`() {
            val steps = listOf(
                step(0, "aaa", "bbb", renameClass("com.A", "AA")),
                step(1, "bbb", "ccc", renameClass("com.B", "BB")),
                step(2, "ccc", "ddd", spec = RefactoringSpec.Other),
                step(3, "ddd", "eee", renameClass("com.C", "CC")),
                step(4, "eee", "fff", renameClass("com.D", "DD")),
                step(5, "fff", "ggg", spec = null),
                step(6, "ggg", "hhh", renameClass("com.E", "EE")),
                step(7, "hhh", "iii", renameClass("com.F", "FF")),
            )
            val validations = mapOf(
                0 to validation(0, Status.VALID),
                1 to validation(1, Status.VALID),
                2 to validation(2, Status.UNTYPED, reason = "RefactoringSpec.Other"),
                3 to validation(3, Status.VALID),
                4 to validation(4, Status.VALID),
                5 to validation(5, Status.UNTYPED, reason = "no typed RefactoringSpec"),
                6 to validation(6, Status.VALID),
                7 to validation(7, Status.VALID),
            )
            inSplitContext { ctx ->
                val lines = mutableListOf<String>()
                val summary = ctx.synth.run(steps, validations) { lines += it }

                assertEquals(3, summary.totalWindows)
                assertEquals(3, summary.eligibleWindows)
                assertEquals(0, summary.singletonWindows)
                assertEquals(6, summary.typedCount)
                assertEquals(2, summary.untypedCount)
                assertEquals(0, summary.divergentCount)
                assertTrue(lines.any { "window #0 aaa→ccc" in it })
                assertTrue(lines.any { "window #1 ddd→fff" in it })
                assertTrue(lines.any { "window #2 ggg→iii" in it })
                assertTrue(lines.any { "split at step #2 (UNTYPED" in it })
                assertTrue(lines.any { "split at step #5 (UNTYPED" in it })
            }
        }

        @Test
        fun `ast diverged step acts as splitter and is counted`() {
            val steps = listOf(
                step(0, "aaa", "bbb", renameClass("com.A", "AA")),
                step(1, "bbb", "ccc", renameClass("com.B", "BB")),
                step(2, "ccc", "ddd", renameClass("com.C", "CC")),
                step(3, "ddd", "eee", renameClass("com.D", "DD")),
            )
            val validations = mapOf(
                0 to validation(0, Status.VALID),
                1 to validation(1, Status.AST_DIVERGED, reason = "file-content mismatch"),
                2 to validation(2, Status.VALID),
                3 to validation(3, Status.VALID),
            )
            inSplitContext { ctx ->
                val lines = mutableListOf<String>()
                val summary = ctx.synth.run(steps, validations) { lines += it }
                assertEquals(2, summary.totalWindows)
                assertEquals(1, summary.eligibleWindows)
                assertEquals(1, summary.singletonWindows)
                assertEquals(3, summary.typedCount)
                assertEquals(1, summary.divergentCount)
                assertEquals(0, summary.refactorFailedCount)
                assertTrue(lines.any { "split at step #1 (AST_DIVERGED — file-content mismatch)" in it })
            }
        }

        @Test
        fun `refactor failed step acts as splitter and is counted`() {
            val steps = listOf(
                step(0, "aaa", "bbb", renameClass("com.A", "AA")),
                step(1, "bbb", "ccc", renameClass("com.B", "BB")),
                step(2, "ccc", "ddd", renameClass("com.C", "CC")),
                step(3, "ddd", "eee", renameClass("com.D", "DD")),
                step(4, "eee", "fff", renameClass("com.E", "EE")),
            )
            val validations = mapOf(
                0 to validation(0, Status.VALID),
                1 to validation(1, Status.VALID),
                2 to validation(2, Status.REFACTOR_FAILED, reason = "anchor not found"),
                3 to validation(3, Status.VALID),
                4 to validation(4, Status.VALID),
            )
            inSplitContext { ctx ->
                val lines = mutableListOf<String>()
                val summary = ctx.synth.run(steps, validations) { lines += it }
                assertEquals(2, summary.totalWindows)
                assertEquals(2, summary.eligibleWindows)
                assertEquals(0, summary.singletonWindows)
                assertEquals(4, summary.typedCount)
                assertEquals(1, summary.refactorFailedCount)
                assertTrue(lines.any { "split at step #2 (REFACTOR_FAILED — anchor not found)" in it })
            }
        }

        @Test
        fun `step missing from validations map is treated as splitter`() {
            val steps = listOf(
                step(0, "aaa", "bbb", renameClass("com.A", "AA")),
                step(1, "bbb", "ccc", renameClass("com.B", "BB")),
            )
            inSplitContext { ctx ->
                val lines = mutableListOf<String>()
                val summary = ctx.synth.run(steps, validations = emptyMap()) { lines += it }
                assertEquals(0, summary.totalWindows)
                assertEquals(0, summary.eligibleWindows)
                assertTrue(lines.any { "split at step #0 (UNVALIDATED)" in it })
            }
        }

        @Test
        fun `empty step list returns zero counts and emits nothing`() {
            inSplitContext { ctx ->
                val lines = mutableListOf<String>()
                val summary = ctx.synth.run(emptyList(), emptyMap()) { lines += it }
                assertEquals(0, summary.totalWindows)
                assertEquals(0, summary.eligibleWindows)
                assertEquals(0, summary.singletonWindows)
                assertEquals(0, summary.typedCount)
                assertTrue(lines.isEmpty())
            }
        }
    }

    @Nested
    inner class Synthesis {

        @Test
        fun `dfs visits each prefix once across all orderings`(@TempDir tmp: Path) {
            // 3 specs, all pairwise-commuting RenameClass → 6 orderings
            // total. Drop identity [0,1,2] → 5 alt orderings.
            // Distinct non-empty prefixes across the alt orderings:
            //   length-1: {0},{1},{2} → 3
            //   length-2: {0,2},{1,0},{1,2},{2,0},{2,1} → 5
            //   length-3: each of the 5 alt orderings → 5
            // Total = 13.
            val steps = (0 until 3).map {
                step(it, sha(it), sha(it + 1), renameClass("com.A$it", "B$it"))
            }
            withSynthCtx(tmp) { ctx ->
                val summary = ctx.synth.run(steps, allValid(steps)) { ctx.lines += it }
                assertEquals(13, summary.appliesIssued, "expected 13 distinct non-empty prefixes for N=3 alt orderings")
                // applies == backtracks for a clean DFS (each non-root
                // node forwards once and backtracks once).
                assertEquals(summary.appliesIssued, summary.backtracksIssued)
                assertEquals(13, ctx.applyCount, "fake apply count must match")
                // Every alt ordering completes.
                val orderings = summary.trajectories[0].orderings
                assertEquals(5, orderings.size)
                assertTrue(orderings.all { it.terminalSuccess })
            }
        }

        @Test
        fun `branch ref shared across orderings with same prefix`(@TempDir tmp: Path) {
            // 3-spec window. Two alt orderings sharing prefix [a] for
            // some `a` should list the same SHA + ref at index 0.
            val steps = (0 until 3).map {
                step(it, sha(it), sha(it + 1), renameClass("com.A$it", "B$it"))
            }
            withSynthCtx(tmp) { ctx ->
                val summary = ctx.synth.run(steps, allValid(steps)) { ctx.lines += it }
                val orderings = summary.trajectories[0].orderings
                val byLead = orderings.groupBy { it.permutation.first() }
                for ((_, group) in byLead) {
                    val refs0 = group.map { it.branchRefs[0] }.distinct()
                    val shas0 = group.map { it.stepShas[0] }.distinct()
                    assertEquals(1, refs0.size, "orderings sharing prefix should share depth-1 ref: $refs0")
                    assertEquals(1, shas0.size, "orderings sharing prefix should share depth-1 sha: $shas0")
                }
                // Refs use the per-prefix path namespace.
                orderings.flatMap { it.branchRefs }.forEach { ref ->
                    assertTrue(
                        ref.matches(Regex("reorder/win\\d+/path/(\\d+)(-\\d+)*")),
                        "ref '$ref' doesn't match per-prefix path namespace",
                    )
                }
            }
        }

        @Test
        fun `partial failure in subtree skips subtree continues siblings`(@TempDir tmp: Path) {
            // Fake fails when applying spec #1 unconditionally. Every
            // alt ordering applies spec 1 at some depth; that ordering
            // fails at that depth. Sibling subtrees not passing
            // through the failing spec still get explored.
            val steps = (0 until 3).map {
                step(it, sha(it), sha(it + 1), renameClass("com.A$it", "B$it"))
            }
            withSynthCtx(tmp, failOnSpecIdx = 1) { ctx ->
                val summary = ctx.synth.run(steps, allValid(steps)) { ctx.lines += it }
                val orderings = summary.trajectories[0].orderings
                for (o in orderings) {
                    assertTrue(!o.terminalSuccess, "ordering ${o.permutation} should fail (it applies spec 1)")
                    val expectedDepth = o.permutation.indexOf(1)
                    assertEquals(
                        expectedDepth, o.failedAt,
                        "ordering ${o.permutation} should fail at depth where spec 1 appears (= ${expectedDepth})",
                    )
                    assertEquals(expectedDepth, o.stepShas.size)
                }
            }
        }

        @Test
        fun `user identity ordering is dropped`(@TempDir tmp: Path) {
            val steps = (0 until 2).map {
                step(it, sha(it), sha(it + 1), renameClass("com.A$it", "B$it"))
            }
            withSynthCtx(tmp) { ctx ->
                val summary = ctx.synth.run(steps, allValid(steps)) { ctx.lines += it }
                assertEquals(1, summary.orderingsSynthesised)
                val traj = summary.trajectories[0]
                assertEquals(listOf(1, 0), traj.orderings[0].permutation)
                assertTrue(traj.orderings.none { it.permutation == listOf(0, 1) })
                assertEquals(2, traj.windowSpecLabels.size)
                assertNotNull(traj.orderings[0].permutationLabels)
            }
        }

        @Test
        fun `windows smaller than 2 are skipped`(@TempDir tmp: Path) {
            val steps = listOf(step(0, "aaa", "bbb", renameClass("com.A", "AA")))
            withSynthCtx(tmp) { ctx ->
                val summary = ctx.synth.run(steps, allValid(steps)) { ctx.lines += it }
                assertEquals(1, summary.totalWindows)
                assertEquals(0, summary.eligibleWindows)
                assertEquals(1, summary.singletonWindows)
                assertEquals(0, summary.orderingsSynthesised)
                assertEquals(0, summary.commitsCreated)
                assertEquals(0, ctx.applyCount)
            }
        }

        @Test
        fun `commits are reachable in shadow repo via the per-prefix refs`(@TempDir tmp: Path) {
            val steps = (0 until 2).map {
                step(it, sha(it), sha(it + 1), renameClass("com.A$it", "B$it"))
            }
            withSynthCtx(tmp) { ctx ->
                val summary = ctx.synth.run(steps, allValid(steps)) { ctx.lines += it }
                val ord = summary.trajectories[0].orderings[0]
                ord.branchRefs.forEachIndexed { k, ref ->
                    assertEquals(
                        ord.stepShas[k],
                        GitRunner(ctx.shadowRepo).revParse(ref),
                        "ref '$ref' should resolve to stepShas[$k]",
                    )
                }
            }
        }
    }

    // -------- fixtures --------

    private fun validation(idx: Int, status: Status, reason: String? = null) =
        StepValidation(stepIndex = idx, status = status, reason = reason, divergedFiles = null)

    private fun allValid(steps: List<RefactoringStep>): Map<Int, StepValidation> =
        steps.associate { it.stepIndex to validation(it.stepIndex, Status.VALID) }

    private fun step(idx: Int, fromSha: String, toSha: String, spec: RefactoringSpec?): RefactoringStep =
        RefactoringStep(
            stepIndex = idx,
            fromSha = fromSha,
            toSha = toSha,
            toCheckpointIndex = idx,
            timestamp = idx.toLong(),
            refactoring = DetectedRefactoring(
                type = "Rename Class",
                description = "Rename Class (test)",
                leftSideLocations = emptyList(),
                rightSideLocations = emptyList(),
                ideRelevant = true,
            ),
            spec = spec,
        )

    private fun renameClass(typeFqn: String, newName: String): RefactoringSpec.RenameClass =
        RefactoringSpec.RenameClass(typeFqn = typeFqn, newName = newName)

    private fun sha(i: Int) = "sha$i".padEnd(40, '0')

    private inline fun inSplitContext(block: (SplitCtx) -> Unit) {
        val tmp = Files.createTempDirectory("rs-split-")
        try {
            initShadowRepo(tmp)
            val pool = WorktreePool(
                shadowRepo = tmp.resolve("shadow-repo"),
                baseDir = tmp.resolve("split-worktrees"),
                size = 1,
            )
            try {
                val synth = ReorderSynthesiser(
                    // Returning Ok without writing files means every alt
                    // ordering hits "no staged changes" and records as
                    // failed — fine, splitting assertions only look at
                    // counts + window log lines.
                    applySpec = { _, _ -> SpecDispatcher.Result.Ok },
                    refreshProject = { /* no-op in tests */ },
                    runInSession = { it() },
                    shadowGit = GitRunner(tmp.resolve("shadow-repo")),
                    pool = pool,
                )
                block(SplitCtxImpl(synth))
            } finally {
                runCatching { pool.close() }
            }
        } finally {
            tmp.toFile().deleteRecursively()
        }
    }

    private inline fun withSynthCtx(
        tmp: Path,
        failOnSpecIdx: Int? = null,
        block: (SynthCtx) -> Unit,
    ) {
        initShadowRepo(tmp)
        val shadow = tmp.resolve("shadow-repo")
        val pool = WorktreePool(shadowRepo = shadow, baseDir = tmp.resolve("synth-worktrees"), size = 1)
        try {
            // Forward-declare ctx so the apply lambda can update it
            // live (rather than via post-block closure capture, which
            // assertions inside the block wouldn't see).
            lateinit var ctx: SynthCtxImpl
            val synth = ReorderSynthesiser(
                applySpec = { spec, worktree ->
                    ctx._applyCount++
                    val rc = spec as? RefactoringSpec.RenameClass
                        ?: error("test fixture only uses RenameClass")
                    val specIdx = rc.newName.removePrefix("B").toInt()  // newName encodes idx
                    if (failOnSpecIdx != null && specIdx == failOnSpecIdx) {
                        SpecDispatcher.Result.Failed("simulated failure on specIdx=$specIdx")
                    } else {
                        // Mutate a spec-unique file so git observes a change.
                        // On backtrack the synthesiser's `git checkout
                        // --detach <parent>` will revert this naturally.
                        val target = worktree.resolve("renames/${rc.typeFqn.replace('.', '_')}.txt")
                        Files.createDirectories(target.parent)
                        target.writeText("renamed=${rc.newName}\n")
                        SpecDispatcher.Result.Ok
                    }
                },
                refreshProject = { /* no-op in tests; real Eclipse project not present */ },
                runInSession = { it() },
                shadowGit = GitRunner(shadow),
                pool = pool,
            )
            ctx = SynthCtxImpl(synth = synth, shadowRepo = shadow)
            block(ctx)
        } finally {
            runCatching { pool.close() }
        }
    }

    private fun initShadowRepo(tmp: Path) {
        val shadow = tmp.resolve("shadow-repo")
        Files.createDirectories(shadow)
        val git = GitRunner(shadow)
        git.init()
        git.setLocalIdentity("test@analysis.local", "Test")
        val seed = shadow.resolve("seed.txt")
        seed.writeText("seed\n")
        git.addAll()
        val seedSha = git.commit("seed")
        // Map common test SHAs → the seed commit so worktree add works.
        val fixtureShas = buildList {
            for (i in 0..10) add("sha$i".padEnd(40, '0'))
            addAll(listOf("aaa", "bbb", "ccc", "ddd", "eee", "fff", "ggg", "hhh", "iii", "jjj"))
        }
        fixtureShas.forEach { git.branchForce(it, seedSha) }
    }

    interface SplitCtx { val synth: ReorderSynthesiser }
    private data class SplitCtxImpl(override val synth: ReorderSynthesiser) : SplitCtx

    interface SynthCtx {
        val synth: ReorderSynthesiser
        val shadowRepo: Path
        val applyCount: Int
        val lines: MutableList<String>
    }
    private class SynthCtxImpl(
        override val synth: ReorderSynthesiser,
        override val shadowRepo: Path,
    ) : SynthCtx {
        @JvmField var _applyCount: Int = 0
        override val applyCount: Int get() = _applyCount
        override val lines: MutableList<String> = mutableListOf()
    }
}
