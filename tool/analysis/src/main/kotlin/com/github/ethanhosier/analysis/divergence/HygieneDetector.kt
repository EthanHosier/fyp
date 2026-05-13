package com.github.ethanhosier.analysis.divergence

import com.github.ethanhosier.analysis.metrics.derived.DerivedMetricsRunner
import com.github.ethanhosier.analysis.metrics.model.AlternativeTrajectory
import com.github.ethanhosier.analysis.metrics.model.CheckpointReport
import com.github.ethanhosier.analysis.metrics.model.DerivedMetrics
import com.github.ethanhosier.analysis.metrics.model.DivergenceKind
import com.github.ethanhosier.analysis.miner.model.RefactoringStep

/**
 * Detects hygiene-flavoured divergences — moments where the user made a
 * *process decision* that hurt their trajectory even though the code at
 * that moment is identical to a hypothetical "better-process" version.
 * Two sub-kinds today:
 *
 *  - `TESTS_SKIPPED` — the user skipped tests at a checkpoint. The
 *    counterfactual flips `tests.wasSkipped = false, tests.success = true`
 *    on that checkpoint, lifting the W_SKIP_TESTS penalty (≈14 points,
 *    pro-rated over `total / refactoringStepsCount`). Honest because the
 *    code is identical — only the decision to run tests changed.
 *  - `COMMIT_GAP` — the user went `MIN_COMMIT_GAP` checkpoints without
 *    committing. The counterfactual flips `isUserCommit = true` on the
 *    anchor checkpoint. **Magnitude is 0 today** because commit cadence
 *    isn't a term in the process score yet; the infrastructure lands now
 *    so that when cadence is added, every existing COMMIT_GAP alt
 *    immediately produces a real Δ without any second pass on schema /
 *    detector / UI.
 *
 * Skipped on purpose:
 *  - `BUILD_BROKEN` / `TESTS_RED` — no honest simulation of "what code
 *    would have made it pass". Flipping `build.success = true` on the
 *    existing code would be wishful thinking.
 *
 * **One flip per alt.** Each finding mutates exactly one checkpoint —
 * the anchor — keeping each counterfactual a clean "what if you'd done
 * this one thing" rather than a batched "what if you'd been better for
 * 10 steps". Long commit gaps produce multiple findings (one per
 * recommended commit point); the walker resets the gap clock at each
 * synthetic commit.
 */
object HygieneDetector {

    /** Anchor checkpoint count past the previous user commit at which
     *  COMMIT_GAP fires. Matches [com.github.ethanhosier.analysis.advice.TrajectoryAdvisor]'s
     *  trajectory-wide INFO threshold so the per-step localisation
     *  agrees with the trajectory-wide rule. */
    const val MIN_COMMIT_GAP: Int = 6

    enum class SubKind { TESTS_SKIPPED, COMMIT_GAP }

    data class Finding(
        val subKind: SubKind,
        /** Anchor checkpoint index — DP and alt anchor here. */
        val anchorIndex: Int,
        /** COMMIT_GAP only: number of checkpoints since the previous
         *  commit (or session start). Surfaced in the explanation
         *  template. */
        val gapLength: Int? = null,
    )

    /**
     * Run the predicates and return findings. Pure — no I/O.
     */
    fun detect(checkpoints: List<CheckpointReport>): List<Finding> {
        if (checkpoints.isEmpty()) return emptyList()
        val out = mutableListOf<Finding>()

        // TESTS_SKIPPED — every checkpoint that recorded a skipped test
        // result. Doesn't require the checkpoint to be a refactoring
        // step's post-state; any skip is a real penalty in the score
        // formula. The W_SKIP_TESTS penalty is rate-based so even a
        // single skip moves the terminal process score.
        for (i in checkpoints.indices) {
            if (checkpoints[i].metrics.tests.wasSkipped) {
                out += Finding(SubKind.TESTS_SKIPPED, anchorIndex = i)
            }
        }

        // COMMIT_GAP — walk emitting one finding per MIN_COMMIT_GAP run
        // since the previous (real or synthetic) commit. Treating each
        // emitted finding as if the user had committed means a long gap
        // yields multiple distinct DPs (one per recommended commit
        // point) instead of one giant DP.
        var lastCommitIdx = -1
        for (i in checkpoints.indices) {
            if (checkpoints[i].isUserCommit) {
                lastCommitIdx = i
                continue
            }
            val gap = i - lastCommitIdx
            if (gap >= MIN_COMMIT_GAP) {
                out += Finding(SubKind.COMMIT_GAP, anchorIndex = i, gapLength = gap)
                lastCommitIdx = i
            }
        }

        return out
    }

    /**
     * Synthesise an [AlternativeTrajectory] per finding by mutating the
     * anchor checkpoint and recomputing process scores via
     * [DerivedMetricsRunner.run]. The alt's `altCheckpoints[0]` carries
     * the recomputed score at the anchor; `continuationCheckpoints`
     * carries the recomputed scores for the rest of the trajectory so
     * the chart can roll the alt's line forward through the user's
     * subsequent steps (same shape as REWORK / IDE_REPLAY alts).
     */
    fun buildAlts(
        findings: List<Finding>,
        checkpoints: List<CheckpointReport>,
        refactoringSteps: List<RefactoringStep>,
    ): List<AlternativeTrajectory> = findings.map { f ->
        val anchorCp = checkpoints[f.anchorIndex]

        // Mutate the anchor checkpoint with the sub-kind-specific flip.
        // Only the anchor is mutated — one flip per alt.
        val mutated = checkpoints.toMutableList()
        mutated[f.anchorIndex] = applyFlip(anchorCp, f.subKind)

        // Re-run the process-score walk over the mutated trajectory.
        // No alts on this second pass — we only need the recomputed
        // main-pass scores so we can anchor the alt and roll its line
        // forward as a continuation.
        val recomputed = DerivedMetricsRunner().run(
            mainCheckpoints = mutated,
            alternatives = emptyList(),
            refactoringSteps = refactoringSteps,
        )

        // Synthetic SHA so the alt's checkpoint doesn't collide with
        // the user's anchor SHA in keyed lookups. The patches /
        // tracking maps simply won't have entries under this SHA, and
        // every downstream consumer falls back to EMPTY defaults.
        val synthSha = "hygiene-${f.subKind.name.lowercase()}-${anchorCp.sha}"
        val altCp = mutated[f.anchorIndex].copy(
            sha = synthSha,
            derivedMetrics = recomputed.main[anchorCp.sha] ?: DerivedMetrics.EMPTY,
        )

        // Continuation: recomputed scores for every user checkpoint
        // after the anchor, overlaid on the user's original checkpoints
        // (static metrics don't change — only the cumulative process
        // score does). Mirrors the existing IDE_REPLAY / REWORK
        // continuation pattern.
        val continuation = (f.anchorIndex + 1..checkpoints.lastIndex).mapNotNull { i ->
            val userCp = checkpoints[i]
            val score = recomputed.main[userCp.sha]?.process ?: return@mapNotNull null
            userCp.copy(
                derivedMetrics = userCp.derivedMetrics.copy(process = score),
            )
        }

        val fromSha = if (f.anchorIndex == 0) {
            anchorCp.sha
        } else {
            checkpoints[f.anchorIndex - 1].sha
        }

        AlternativeTrajectory(
            kind = DivergenceKind.HYGIENE,
            // Empty stepIndexes — HYGIENE alts don't correspond to a
            // refactoring step. The DP carries the anchor checkpoint
            // index directly. Same shape as REWORK alts.
            stepIndexes = emptyList(),
            fromSha = fromSha,
            userToSha = anchorCp.sha,
            branchRefs = emptyList(),
            specs = emptyList(),
            altCheckpoints = listOf(altCp),
            // Single flipped checkpoint anchored at the user's anchor
            // checkpoint xPos — REWORK chart trick keeps the polyline
            // from squashing into a compressed slot window.
            altCheckpointUserIndexes = listOf(f.anchorIndex),
            continuationCheckpoints = continuation,
        )
    }

    private fun applyFlip(cp: CheckpointReport, subKind: SubKind): CheckpointReport = when (subKind) {
        SubKind.TESTS_SKIPPED -> cp.copy(
            metrics = cp.metrics.copy(
                tests = cp.metrics.tests.copy(success = true, wasSkipped = false),
            ),
        )
        SubKind.COMMIT_GAP -> cp.copy(isUserCommit = true)
    }
}
