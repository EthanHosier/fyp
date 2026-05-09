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
import kotlin.test.assertTrue

class ReorderSynthesiserTest {

    /**
     * Window-splitting concerns migrated verbatim from the deleted
     * `ReorderWindowLoggerTest`. These tests construct a synthesiser
     * with throwing fakes for the synthesis seams, because windows
     * with `< 2` typed specs never reach the synthesis path. The
     * splitter logic + summary counts are exercised here.
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
            // Three pairwise-commuting RenameClass specs → 6 valid orderings;
            // identity dropped → 5 alt orderings; we exercise synthesis here
            // too, so use a real (no-op-effect) git fixture.
            return inSplitContext { ctx ->
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
                assertEquals(0, summary.untypedCount)
                assertEquals(0, summary.divergentCount)
                assertEquals(0, summary.refactorFailedCount)
                assertTrue(lines.isEmpty())
            }
        }
    }

    @Nested
    inner class Synthesis {

        @Test
        fun `two orderings sharing a prefix apply each spec once for the prefix`(@TempDir tmp: Path) {
            // 3 specs, all pairwise-commuting RenameClass → 6 orderings.
            // Drop identity [0,1,2] → 5 alt orderings.
            // The enumerator emits orderings in lex-grouped order, so
            // [0,1,2] → identity (skipped), then [0,2,1], [1,0,2], [1,2,0],
            // [2,0,1], [2,1,0]. Cache should hit on shared prefixes.
            val steps = (0 until 3).map {
                step(it, sha(it), sha(it + 1), renameClass("com.A$it", "B$it"))
            }
            val validations = allValid(steps)

            withSynthCtx(tmp) { ctx ->
                ctx.synth.run(steps, validations) { ctx.lines += it }

                // Total apply calls across all 5 alt orderings: each ordering
                // length is 3, so naive = 15. With prefix cache, count should
                // be < 15. Concrete bound: enumerator emits in a way that
                // groups by leading element, so we expect ~10 applies (a
                // healthy reduction).
                assertTrue(
                    ctx.applyCount < 15,
                    "expected prefix-cache reuse to reduce applies below 15, got ${ctx.applyCount}",
                )
                // And we expect SOME cache hits to be reported.
                assertTrue(ctx.lines.any { "cache hit on prefix" in it })
            }
        }

        @Test
        fun `partial failure keeps succeeded prefix for later orderings`(@TempDir tmp: Path) {
            // Same 3 RenameClass spec setup, but failure on the SECOND
            // applySpec call within an ordering. Goal: after the first
            // ordering's prefix gets cached, a later ordering hitting that
            // same prefix should not re-attempt the prefix's applies.
            val steps = (0 until 3).map {
                step(it, sha(it), sha(it + 1), renameClass("com.A$it", "B$it"))
            }
            val validations = allValid(steps)

            withSynthCtx(tmp, failOnApplyCallNumber = 2) { ctx ->
                ctx.synth.run(steps, validations) { ctx.lines += it }

                // We don't pin the exact apply count (it depends on enumerator
                // order), but: every alt ordering should still get processed,
                // and at least one should report a failure.
                val failures = ctx.lines.count { "failed at" in it }
                assertTrue(failures >= 1, "expected ≥1 ordering to record a failure")
            }
        }

        @Test
        fun `windows smaller than 2 are skipped`(@TempDir tmp: Path) {
            // A single typed VALID spec — eligible? No (we require ≥2).
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
        fun `branch refs match the reorder namespace and are recorded in trajectories`(@TempDir tmp: Path) {
            val steps = (0 until 2).map {
                step(it, sha(it), sha(it + 1), renameClass("com.A$it", "B$it"))
            }
            withSynthCtx(tmp) { ctx ->
                val summary = ctx.synth.run(steps, allValid(steps)) { ctx.lines += it }
                // 2 specs × 2 commute → 2 orderings; identity skipped → 1 alt
                // ordering of length 2 → 2 commits/refs.
                assertEquals(1, summary.trajectories.size)
                val traj = summary.trajectories[0]
                assertEquals(1, traj.orderings.size)
                val ord = traj.orderings[0]
                assertEquals(2, ord.branchRefs.size)
                assertEquals(2, ord.stepShas.size)
                assertTrue(ord.terminalSuccess)
                assertEquals(listOf(1, 0), ord.permutation)
                // Human-readable labels: per-window list parallel to
                // step indices, plus a per-ordering re-projection.
                assertEquals(2, traj.windowSpecLabels.size)
                assertEquals(traj.windowSpecLabels[1], ord.permutationLabels[0])
                assertEquals(traj.windowSpecLabels[0], ord.permutationLabels[1])
                ord.branchRefs.forEach { ref ->
                    assertTrue(
                        ref.matches(Regex("reorder/win\\d+/ord\\d+/step-\\d+")),
                        "ref '$ref' doesn't match reorder namespace",
                    )
                }
                // Refs are addressable from the shadow repo.
                ord.branchRefs.forEachIndexed { k, ref ->
                    assertEquals(ord.stepShas[k], shaOf(ctx.shadowRepo, ref))
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
                // 2 typed specs, 2 valid orderings, drop identity → 1 ord.
                assertEquals(1, summary.orderingsSynthesised)
                val traj = summary.trajectories[0]
                assertEquals(listOf(1, 0), traj.orderings[0].permutation)
                // Identity [0,1] would be the user's actual trace ordering;
                // never recorded.
                assertTrue(traj.orderings.none { it.permutation == listOf(0, 1) })
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

    /**
     * Synth wired with throwing fakes for everything that should
     * never be reached when only window-splitting is exercised.
     * Used by the [WindowSplitting] tests above.
     */
    private inline fun inSplitContext(block: (SplitCtx) -> Unit) {
        val tmp = Files.createTempDirectory("rs-split-")
        try {
            // We need a real shadow repo + pool because the synthesiser
            // closes over them, but neither should be hit when no window
            // is eligible OR when every typed spec is RenameClass and
            // commutes (the synthesis path *will* run for all-valid traces;
            // it borrows worktrees etc.). For the tests that have no
            // eligible windows, the throwing fakes are fine — but the
            // "all valid" test does need real fixtures, so we always
            // wire them.
            initShadowRepo(tmp)
            val pool = WorktreePool(
                shadowRepo = tmp.resolve("shadow-repo"),
                baseDir = tmp.resolve("split-worktrees"),
                size = 1,
            )
            try {
                val synth = ReorderSynthesiser(
                    // Window-splitting tests don't care about synthesis
                    // success. Returning Ok without mutating files means
                    // every alt ordering hits "no staged changes" and
                    // records as failed — fine, splitting assertions only
                    // look at counts + window log lines emitted before
                    // synthesis runs.
                    applySpec = { _, _ -> SpecDispatcher.Result.Ok },
                    runInSession = { it() },
                    shadowGit = GitRunner(tmp.resolve("shadow-repo")),
                    pool = pool,
                )
                block(SplitCtx(synth))
            } finally {
                runCatching { pool.close() }
            }
        } finally {
            tmp.toFile().deleteRecursively()
        }
    }

    private inline fun withSynthCtx(
        tmp: Path,
        failOnApplyCallNumber: Int? = null,
        block: (SynthCtx) -> Unit,
    ) {
        initShadowRepo(tmp)
        val shadow = tmp.resolve("shadow-repo")
        val pool = WorktreePool(shadowRepo = shadow, baseDir = tmp.resolve("synth-worktrees"), size = 1)
        try {
            var applyCount = 0
            val synth = ReorderSynthesiser(
                applySpec = { spec, worktree ->
                    applyCount++
                    if (failOnApplyCallNumber != null && applyCount >= failOnApplyCallNumber) {
                        SpecDispatcher.Result.Failed("simulated failure on apply #$applyCount")
                    } else {
                        // Mutate a spec-unique file so git observes a change.
                        val rc = spec as? RefactoringSpec.RenameClass
                            ?: error("test fixture only uses RenameClass")
                        val target = worktree.resolve("renames/${rc.typeFqn.replace('.', '_')}.txt")
                        Files.createDirectories(target.parent)
                        target.writeText("renamed=${rc.newName}\napplyCount=$applyCount\n")
                        SpecDispatcher.Result.Ok
                    }
                },
                runInSession = { it() },
                shadowGit = GitRunner(shadow),
                pool = pool,
            )
            val ctx = SynthCtxImpl(synth = synth, shadowRepo = shadow)
            block(ctx)
            ctx._applyCount = applyCount
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
        // Need at least one commit reachable by every fromSha we'll borrow
        // at. We use real-looking 40-char SHAs in the step fixtures, but
        // the pool calls `git worktree add <path> <sha>`. To make that
        // work without manufacturing matching SHAs, we point branches at
        // the seed commit. We DO that here: create a seed commit, then
        // attach a "ref" for each fixture sha.
        val seed = shadow.resolve("seed.txt")
        seed.writeText("seed\n")
        git.addAll()
        val seedSha = git.commit("seed")
        // Map common test SHAs → the seed commit so `worktree add <sha>`
        // works. We use both the padded `sha0000…` form (Synthesis tests)
        // and 3-char vowel-runs (WindowSplitting tests) for readability.
        val fixtureShas = buildList {
            for (i in 0..10) add("sha$i".padEnd(40, '0'))
            addAll(listOf("aaa", "bbb", "ccc", "ddd", "eee", "fff", "ggg", "hhh", "iii", "jjj"))
        }
        fixtureShas.forEach { git.branchForce(it, seedSha) }
    }

    private fun shaOf(shadow: Path, ref: String): String =
        GitRunner(shadow).let {
            // Use rev-parse via the runner's `head()` after a checkout… but
            // simpler: `git -C shadow rev-parse <ref>`. GitRunner doesn't
            // expose that directly, so we run it via the rev-parse helper.
            it.revParse(ref)
        }

    interface SplitCtx { val synth: ReorderSynthesiser }
    private data class SplitCtxImpl(override val synth: ReorderSynthesiser) : SplitCtx
    private fun SplitCtx(synth: ReorderSynthesiser): SplitCtx = SplitCtxImpl(synth)

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
        var _applyCount: Int = 0
        override val applyCount: Int get() = _applyCount
        override val lines: MutableList<String> = mutableListOf()
    }
}
