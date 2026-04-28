package com.github.ethanhosier.analysis.metrics

import com.github.ethanhosier.analysis.metrics.ck.CkRunner
import com.github.ethanhosier.analysis.metrics.cpd.CpdRunner
import com.github.ethanhosier.analysis.metrics.gitdiff.DiffRunner
import com.github.ethanhosier.analysis.metrics.readability.ReadabilityRunner
import com.github.ethanhosier.analysis.metrics.gitdiff.DiffStats
import com.github.ethanhosier.analysis.metrics.gradlebuild.GradleBuildRunner
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.metrics.pmd.PmdRunner
import com.github.ethanhosier.analysis.metrics.tests.GradleTestRunner
import com.github.ethanhosier.analysis.metrics.tests.TestResult
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Computes [CheckpointMetrics] for every unique commit SHA produced by the
 * reconstruct stage.
 *
 * Unique-SHA dedup matters because a long session produces hundreds of events
 * but far fewer unique commits — every no-op event collapses onto its
 * preceding SHA.
 *
 * Sequential single-worktree strategy: provision one persistent worktree
 * off the shadow repo, then walk SHAs by `git checkout --detach <sha>`
 * between them — preserving the worktree's `build/` so Gradle's daemon +
 * `--build-cache` + incremental-compile machinery has prior state to
 * reuse. Empirically this beats parallel-fresh-worktree by ~2× on small
 * sessions because daemon warmup + cache reuse dominates over wall-clock
 * fan-out at this scale.
 *
 * The [parallelism] parameter is retained for API compatibility (the CLI
 * still accepts `--parallelism=N`) but is now ignored — metrics run
 * sequentially regardless.
 *
 * No on-disk caching of metrics output: each pipeline run recomputes every
 * SHA. Sessions are processed once and surrounding orchestration assumes
 * that, so caching the report adds invalidation surface for no real win.
 */
class MetricsRunner(
    @Suppress("UNUSED_PARAMETER") parallelism: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1),
    private val gradleUserHome: Path? = null,
    private val buildTimeout: Duration = Duration.ofMinutes(5),
    private val testTimeout: Duration = Duration.ofMinutes(10),
) {

    data class Summary(
        val totalShas: Int,
        val computed: Int,
        val buildOk: Int,
        val testsOk: Int,
        // Ordered by first-appearance of the SHA in the normalized event stream,
        // so callers can build a chronological report without re-deriving order.
        val checkpoints: List<CheckpointMetrics>,
        // Keyed by SHA. Diff is vs. the previous checkpoint (or the seed commit
        // for the first).
        val diffBySha: Map<String, DiffStats> = emptyMap(),
        // Metrics for any synthesised alternative-trajectory SHAs the caller
        // passed via [run]'s `alternativeShas` parameter. Sibling of
        // [checkpoints].
        val alternativeCheckpoints: List<CheckpointMetrics> = emptyList(),
    )

    /**
     * @param reconstruction the user's actual trace; each unique SHA in
     *   `eventCommits` becomes a checkpoint.
     * @param alternativeShas synthesised SHAs (e.g. from
     *   `AlternativeTrajectoryRunner`) that should be measured alongside
     *   the trace SHAs. Each must already be reachable in
     *   `reconstruction.repoDir` (typically via a ref attached by the
     *   caller). Diff is computed pairwise against
     *   `alternativeFromShas[i]` rather than against the previous SHA in
     *   the list, so non-adjacent ancestor chains work too.
     * @param alternativeFromShas the parent SHA each entry in
     *   [alternativeShas] should be diffed against. Must have the same
     *   length as [alternativeShas]. Empty list when [alternativeShas]
     *   is empty.
     */
    fun run(
        reconstruction: ReconstructionResult,
        sessionFolder: Path,
        alternativeShas: List<String> = emptyList(),
        alternativeFromShas: List<String> = emptyList(),
    ): Summary {
        require(alternativeShas.size == alternativeFromShas.size) {
            "alternativeShas and alternativeFromShas must be parallel; got " +
                "${alternativeShas.size} vs ${alternativeFromShas.size}"
        }

        val worktreeBase = sessionFolder.resolve("shadow-worktrees")
        Files.createDirectories(worktreeBase)
        val worktreeDir = worktreeBase.resolve("metrics")

        // LinkedHashSet: keep chronological iteration order from the event
        // log so progress output lands in the order a human would expect.
        val uniqueShas = reconstruction.eventCommits.mapping.values.toCollection(LinkedHashSet())
        val altShasSet = alternativeShas.toCollection(LinkedHashSet())
        // Union for sequential walk; preserve trace-order, then alt-order
        // so cache reuse runs along the natural commit lineage.
        val allShas = LinkedHashSet<String>().apply {
            addAll(uniqueShas)
            addAll(altShasSet)
        }.toList()

        val git = GitRunner(reconstruction.repoDir)
        // Defensive: clear any stale worktree from a crashed prior run so
        // `worktree add` doesn't fail on an existing directory.
        if (Files.exists(worktreeDir)) {
            runCatching { git.worktreeRemove(worktreeDir) }
            if (Files.exists(worktreeDir)) worktreeDir.toFile().deleteRecursively()
        }
        runCatching { git.worktreePrune() }

        val results = try {
            // Seed the worktree at the first SHA. Subsequent SHAs are
            // reached by `git checkout --detach` *inside* the worktree,
            // preserving its `build/` between checkpoints.
            val first = allShas.first()
            git.worktreeAdd(worktreeDir, first)
            val seeded = computeOne(first, worktreeDir)

            val rest = allShas.drop(1).map { sha ->
                GitRunner(worktreeDir).checkoutDetach(sha)
                computeOne(sha, worktreeDir)
            }
            listOf(seeded) + rest
        } finally {
            runCatching { git.worktreeRemove(worktreeDir) }
            runCatching { git.worktreePrune() }
        }

        val byShaResult = results.associateBy { it.sha }
        val traceCheckpoints = uniqueShas.mapNotNull { byShaResult[it] }
        val altCheckpoints = altShasSet.mapNotNull { byShaResult[it] }

        val diffRunner = DiffRunner(GitRunner(reconstruction.repoDir))
        val traceDiffs = diffRunner.runAll(uniqueShas.toList())
        val altDiffs = diffRunner.runPairs(alternativeFromShas.zip(alternativeShas))
        // Trace diffs first so an alt SHA never silently shadows a real one
        // (no overlap is expected since alt SHAs are distinct synthetic commits).
        val diffBySha = LinkedHashMap<String, DiffStats>().apply {
            putAll(traceDiffs)
            putAll(altDiffs)
        }

        return Summary(
            totalShas = uniqueShas.size,
            computed = allShas.size,
            buildOk = traceCheckpoints.count { it.build.success },
            testsOk = traceCheckpoints.count { it.tests.success },
            checkpoints = traceCheckpoints,
            diffBySha = diffBySha,
            alternativeCheckpoints = altCheckpoints,
        )
    }

    private fun computeOne(sha: String, worktree: Path): CheckpointMetrics {
        val ck = CkRunner().run(worktree)
        val pmd = PmdRunner().run(worktree)
        val cpd = CpdRunner().run(worktree)
        val readability = ReadabilityRunner().run(worktree)
        val build = GradleBuildRunner(
            timeout = buildTimeout,
            gradleUserHome = gradleUserHome,
        ).run(worktree)
        // Skip tests when build failed — `./gradlew test` re-runs compileJava
        // and hits the same error, wasting seconds per broken checkpoint
        // without producing any test outcome we didn't already know.
        val tests = if (build.success) {
            GradleTestRunner(
                timeout = testTimeout,
                gradleUserHome = gradleUserHome,
            ).run(worktree)
        } else {
            TestResult.skipped("build failed (exit ${build.exitCode})")
        }
        return CheckpointMetrics(sha, ck, pmd, cpd, readability, build, tests)
    }
}
