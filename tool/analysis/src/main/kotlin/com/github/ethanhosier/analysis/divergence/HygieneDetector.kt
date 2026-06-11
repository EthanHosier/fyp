package com.github.ethanhosier.analysis.divergence

import com.github.ethanhosier.analysis.metrics.derived.DerivedMetricsRunner
import com.github.ethanhosier.analysis.metrics.derived.ScoringConfig
import com.github.ethanhosier.analysis.metrics.model.AlternativeTrajectory
import com.github.ethanhosier.analysis.pipeline.CheckpointReport
import com.github.ethanhosier.analysis.pipeline.DivergenceKind
import com.github.ethanhosier.analysis.pipeline.EventSummary
import com.github.ethanhosier.analysis.pipeline.ProcessScore
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.ideplugin.model.EventType

class HygieneDetector(
    val config: ScoringConfig = ScoringConfig.PRODUCTION,
) {
    private val weights get() = config.process

    enum class SubKind { TESTS_SKIPPED, COMMIT_GAP }

    data class Finding(
        val subKind: SubKind,
        val anchorIndex: Int,
        val gapLength: Int? = null,
    )

    fun detect(
        checkpoints: List<CheckpointReport>,
        refactoringSteps: List<RefactoringStep> = emptyList(),
    ): List<Finding> {
        if (checkpoints.isEmpty()) return emptyList()
        val out = mutableListOf<Finding>()

        val sortedSteps = refactoringSteps.sortedBy { it.timestamp }
        if (sortedSteps.isNotEmpty()) {
            // Pass 1: group steps into composites.
            data class Composite(val steps: MutableList<RefactoringStep>)
            val composites = mutableListOf<Composite>()
            var current: Composite? = null
            var prevTs: Long? = null
            for (s in sortedSteps) {
                val gap = prevTs?.let { s.timestamp - it } ?: Long.MAX_VALUE
                if (current == null || gap > weights.compositeGapMs) {
                    current = Composite(mutableListOf())
                    composites += current
                }
                current.steps += s
                prevTs = s.timestamp
            }

            // Pass 2: collect TEST_RUN_FINISHED timestamps once.
            val testTimestamps = checkpoints
                .flatMap { it.events }
                .filter { it.type == EventType.TEST_RUN_FINISHED }
                .map { it.timestamp }
                .sorted()

            // Pass 3: emit one finding per untested composite at its
            // last step's checkpoint.
            for (i in composites.indices) {
                val c = composites[i]
                val lo = c.steps.first().timestamp
                val hi = composites.getOrNull(i + 1)?.steps?.first()?.timestamp ?: Long.MAX_VALUE
                val tested = testTimestamps.any { it >= lo && it < hi }
                if (!tested) {
                    val lastStep = c.steps.last()
                    out += Finding(SubKind.TESTS_SKIPPED, anchorIndex = lastStep.toCheckpointIndex)
                }
            }
        }

        val refactorLandingIdx = refactoringSteps.mapTo(HashSet()) { it.toCheckpointIndex }
        var greenSinceLastCommit = 0
        for (i in checkpoints.indices) {
            val cp = checkpoints[i]
            if (cp.isUserCommit) {
                greenSinceLastCommit = 0
                continue
            }
            val landsRefactor = i in refactorLandingIdx
            val green = cp.metrics.build.success &&
                cp.metrics.tests.success &&
                !cp.metrics.tests.wasSkipped
            if (landsRefactor && green) {
                greenSinceLastCommit += 1
                if (greenSinceLastCommit >= weights.minCommitGap) {
                    out += Finding(
                        SubKind.COMMIT_GAP,
                        anchorIndex = i,
                        gapLength = greenSinceLastCommit,
                    )
                    greenSinceLastCommit = 0
                }
            }
        }

        return out
    }

    fun buildAlts(
        findings: List<Finding>,
        checkpoints: List<CheckpointReport>,
        refactoringSteps: List<RefactoringStep>,
    ): List<AlternativeTrajectory> = findings.map { f ->
        val anchorCp = checkpoints[f.anchorIndex]
        val anchorTs = refactoringSteps
            .firstOrNull { it.toCheckpointIndex == f.anchorIndex }?.timestamp
            ?: anchorCp.events.maxOfOrNull { it.timestamp }
            ?: 0L

        // Mutate the anchor checkpoint with the sub-kind-specific flip.
        // Only the anchor is mutated — one flip per alt.
        val mutated = checkpoints.toMutableList()
        mutated[f.anchorIndex] = applyFlip(anchorCp, f.subKind, anchorTs)

        val recomputed = DerivedMetricsRunner(config).run(
            mainCheckpoints = mutated,
            alternatives = emptyList(),
            refactoringSteps = refactoringSteps,
        )

        val synthSha = "hygiene-${f.subKind.name.lowercase()}-${anchorCp.sha}"
        val recomputedProcess = recomputed.main[anchorCp.sha]?.process ?: ProcessScore.EMPTY
        val altCp = mutated[f.anchorIndex].copy(
            sha = synthSha,
            derivedMetrics = anchorCp.derivedMetrics.copy(process = recomputedProcess),
            metricsTrustworthy = anchorCp.metricsTrustworthy,
            metricsCarryForwardSource = anchorCp.metricsCarryForwardSource,
        )

        val continuation = (f.anchorIndex + 1..checkpoints.lastIndex).mapNotNull { i ->
            val userCp = checkpoints[i]
            val score = recomputed.main[userCp.sha]?.process ?: return@mapNotNull null
            userCp.copy(
                derivedMetrics = userCp.derivedMetrics.copy(process = score),
            )
        }

        AlternativeTrajectory(
            kind = DivergenceKind.HYGIENE,
            stepIndexes = emptyList(),
            fromSha = anchorCp.sha,
            userToSha = anchorCp.sha,
            branchRefs = emptyList(),
            specs = emptyList(),
            altCheckpoints = listOf(altCp),
            altCheckpointUserIndexes = listOf(f.anchorIndex),
            continuationCheckpoints = continuation,
        )
    }

    private fun applyFlip(cp: CheckpointReport, subKind: SubKind, ts: Long): CheckpointReport = when (subKind) {
        SubKind.TESTS_SKIPPED -> cp.copy(
            events = cp.events + EventSummary(
                id = "hygiene-synthetic-test-run-${cp.sha}",
                type = EventType.TEST_RUN_FINISHED,
                timestamp = ts,
            ),
        )
        SubKind.COMMIT_GAP -> cp.copy(isUserCommit = true)
    }
}
