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
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class MetricsRunner(
    @Suppress("UNUSED_PARAMETER") parallelism: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1),
    private val gradleUserHome: Path? = null,
    private val buildTimeout: Duration = Duration.ofMinutes(5),
    private val testTimeout: Duration = Duration.ofMinutes(10),
) {

    @Serializable
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
        val alternativeCheckpoints: List<CheckpointMetrics> = emptyList(),
    )

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
        val pmdCacheFile = sessionFolder.resolve("pmd-analysis-cache")
        val readabilityCacheFile = sessionFolder.resolve("pmd-readability-cache")

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

        val total = allShas.size
        val results = try {
            val first = allShas.first()
            git.worktreeAdd(worktreeDir, first)
            logProgress(1, total, first)
            val seeded = computeOne(first, worktreeDir, pmdCacheFile, readabilityCacheFile)

            val rest = allShas.drop(1).mapIndexed { i, sha ->
                GitRunner(worktreeDir).checkoutDetach(sha)
                logProgress(i + 2, total, sha)
                computeOne(sha, worktreeDir, pmdCacheFile, readabilityCacheFile)
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

    private fun computeOne(
        sha: String,
        worktree: Path,
        pmdCacheFile: Path,
        readabilityCacheFile: Path,
    ): CheckpointMetrics {
        val ck = CkRunner().run(worktree)
        val pmd = PmdRunner(cacheFile = pmdCacheFile).run(worktree)
        val cpd = CpdRunner().run(worktree)
        val readability = ReadabilityRunner(cacheFile = readabilityCacheFile).run(worktree)
        val build = GradleBuildRunner(
            timeout = buildTimeout,
            gradleUserHome = gradleUserHome,
        ).run(worktree)
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

    private fun logProgress(index: Int, total: Int, sha: String) {
        println("[metrics] computing $index/$total sha=${sha.take(8)}")
    }
}
