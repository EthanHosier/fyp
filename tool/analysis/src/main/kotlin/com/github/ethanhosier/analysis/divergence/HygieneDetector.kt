package com.github.ethanhosier.analysis.divergence

import com.github.ethanhosier.analysis.metrics.derived.DerivedMetricsRunner
import com.github.ethanhosier.analysis.metrics.model.AlternativeTrajectory
import com.github.ethanhosier.analysis.metrics.model.CheckpointReport
import com.github.ethanhosier.analysis.metrics.model.DerivedMetrics
import com.github.ethanhosier.analysis.metrics.model.DivergenceKind
import com.github.ethanhosier.analysis.metrics.model.EventSummary
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.ideplugin.model.EventType

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

    /** Number of *green refactor checkpoints* (a refactoring step landed
     *  there AND build + tests both passed) accumulated since the
     *  previous commit at which COMMIT_GAP fires. The count is
     *  cumulative — non-green or non-refactor checkpoints don't break
     *  the run, they just don't contribute. So `1 green → 1 red → 5
     *  greens` fires (6 cumulative greens) where a strict consecutive
     *  counter wouldn't. */
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
    fun detect(
        checkpoints: List<CheckpointReport>,
        refactoringSteps: List<RefactoringStep> = emptyList(),
    ): List<Finding> {
        if (checkpoints.isEmpty()) return emptyList()
        val out = mutableListOf<Finding>()

        // TESTS_SKIPPED — fires only at refactoring-step post-checkpoints
        // that carry no `TEST_RUN_FINISHED` event. This is what the
        // process-score formula penalises (W_SKIP_TESTS in
        // `DerivedMetricsRunner.advanceMainStep`): `testsSkippedCount`
        // only increments at refactoring steps, and the test indicator
        // it reads is the IDE event stream rather than `tests.wasSkipped`
        // (which tracks gradle test runs, a different signal). Anchoring
        // at non-refactoring checkpoints would emit DPs that don't move
        // the recomputed score.
        for (step in refactoringSteps) {
            val cp = checkpoints.getOrNull(step.toCheckpointIndex) ?: continue
            val userRanTests = cp.events.any { it.type == EventType.TEST_RUN_FINISHED }
            if (!userRanTests) {
                out += Finding(SubKind.TESTS_SKIPPED, anchorIndex = step.toCheckpointIndex)
            }
        }

        // COMMIT_GAP — walk counting *green refactor checkpoints* since
        // the previous (real or synthetic) commit. A checkpoint counts
        // iff a refactoring step landed there AND build+tests both
        // passed. Non-green or non-refactor checkpoints don't contribute
        // (and don't break the run). Fires once the cumulative count
        // hits MIN_COMMIT_GAP, then resets — treating the finding as
        // if the user had committed there, so long stretches emit one
        // DP per recommended commit point instead of one giant DP.
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
                if (greenSinceLastCommit >= MIN_COMMIT_GAP) {
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

        AlternativeTrajectory(
            kind = DivergenceKind.HYGIENE,
            // Empty stepIndexes — HYGIENE alts don't correspond to a
            // refactoring step. The DP carries the anchor checkpoint
            // index directly. Same shape as REWORK alts.
            stepIndexes = emptyList(),
            // Anchor the divergence at the user checkpoint where the
            // hygiene predicate fires — fromSha == userToSha == anchor.
            // The "decision" (skip tests / not commit) is recorded at
            // this checkpoint, so the alt branches *from* it rather
            // than from the preceding step. Visually the alt polyline
            // starts at the anchor's xPos.
            fromSha = anchorCp.sha,
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
        // Inject a synthetic TEST_RUN_FINISHED event — that's the
        // signal `DerivedMetricsRunner.stepUserRanTests` reads to gate
        // the W_SKIP_TESTS penalty. Also flip the gradle metric flags
        // for UI consistency (the StatusRow renders them) — though
        // those flags don't affect the score directly.
        SubKind.TESTS_SKIPPED -> cp.copy(
            events = cp.events + EventSummary(
                id = "hygiene-synthetic-test-run-${cp.sha}",
                type = EventType.TEST_RUN_FINISHED,
                timestamp = 0L,
            ),
            metrics = cp.metrics.copy(
                tests = cp.metrics.tests.copy(success = true, wasSkipped = false),
            ),
        )
        SubKind.COMMIT_GAP -> cp.copy(isUserCommit = true)
    }
}
