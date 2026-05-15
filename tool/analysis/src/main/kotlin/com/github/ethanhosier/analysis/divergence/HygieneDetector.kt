package com.github.ethanhosier.analysis.divergence

import com.github.ethanhosier.analysis.metrics.derived.DerivedMetricsRunner
import com.github.ethanhosier.analysis.metrics.model.AlternativeTrajectory
import com.github.ethanhosier.analysis.pipeline.CheckpointReport
import com.github.ethanhosier.analysis.pipeline.DivergenceKind
import com.github.ethanhosier.analysis.pipeline.EventSummary
import com.github.ethanhosier.analysis.pipeline.ProcessScore
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

    /** Composite window for TESTS_SKIPPED + `W_SKIP_TESTS`: refactoring
     *  steps whose inter-step gap is ≤ this value belong to one
     *  composite. Following Murphy-Hill, Parnin & Black 2012 (TSE,
     *  "How We Refactor and How We Know It"), who report ~40 % of
     *  tool-invoked refactorings cluster within 60 s of each other. */
    const val COMPOSITE_GAP_MS: Long = 60_000L

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

        // TESTS_SKIPPED — fires once per *untested composite*, anchored
        // at the composite's last step's checkpoint. A composite is a
        // run of consecutive refactoring steps whose inter-step gap is
        // ≤ COMPOSITE_GAP_MS, mirroring `DerivedMetricsRunner`'s
        // composite definition (Murphy-Hill, Parnin & Black 2012).
        // The composite is "tested" iff any `TEST_RUN_FINISHED` event
        // lands in `[firstStep.ts, nextComposite.firstStep.ts)`. This
        // matches the `W_SKIP_TESTS` denominator switch (per-composite,
        // not per-step) so the divergence-point count agrees with the
        // process-score signal — one DP per missed batch, not per
        // missed step.
        val sortedSteps = refactoringSteps.sortedBy { it.timestamp }
        if (sortedSteps.isNotEmpty()) {
            // Pass 1: group steps into composites.
            data class Composite(val steps: MutableList<RefactoringStep>)
            val composites = mutableListOf<Composite>()
            var current: Composite? = null
            var prevTs: Long? = null
            for (s in sortedSteps) {
                val gap = prevTs?.let { s.timestamp - it } ?: Long.MAX_VALUE
                if (current == null || gap > COMPOSITE_GAP_MS) {
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
        // Anchor-step timestamp: used as the synthetic
        // TEST_RUN_FINISHED event's timestamp so it lands inside the
        // composite's `[firstStep.ts, nextComposite.firstStep.ts)`
        // window in `computeCompositeAssignments`. The anchor cp is
        // where the *last* step of an untested composite lands, so
        // that step's timestamp is in the window by construction.
        // Fallback to the cp's max existing event timestamp, or 0 if
        // empty — covers degenerate test fixtures.
        val anchorTs = refactoringSteps
            .firstOrNull { it.toCheckpointIndex == f.anchorIndex }?.timestamp
            ?: anchorCp.events.maxOfOrNull { it.timestamp }
            ?: 0L

        // Mutate the anchor checkpoint with the sub-kind-specific flip.
        // Only the anchor is mutated — one flip per alt.
        val mutated = checkpoints.toMutableList()
        mutated[f.anchorIndex] = applyFlip(anchorCp, f.subKind, anchorTs)

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
        // The whole point of a HYGIENE counterfactual is "same code,
        // different process decision" — so static cleanliness, smells,
        // duplication, etc. MUST match the user's displayed values.
        // Only the process score changes (W_SKIP_TESTS lifts, or
        // future commit-cadence term). We inherit the user anchor's
        // derivedMetrics wholesale and overlay the recomputed process
        // score. Without this, the alt would show the *raw* aggregate
        // at the anchor SHA (because flipping tests.success makes the
        // anchor trustworthy in the mutated run), while the user side
        // carries forward from the last green checkpoint — producing
        // two different cleanliness values for identical code.
        val recomputedProcess = recomputed.main[anchorCp.sha]?.process ?: ProcessScore.EMPTY
        val altCp = mutated[f.anchorIndex].copy(
            sha = synthSha,
            derivedMetrics = anchorCp.derivedMetrics.copy(process = recomputedProcess),
            metricsTrustworthy = anchorCp.metricsTrustworthy,
            metricsCarryForwardSource = anchorCp.metricsCarryForwardSource,
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

    private fun applyFlip(cp: CheckpointReport, subKind: SubKind, ts: Long): CheckpointReport = when (subKind) {
        // Inject a synthetic TEST_RUN_FINISHED event stamped at `ts`
        // so it falls inside the composite's test window in
        // `DerivedMetricsRunner.computeCompositeAssignments`. We do
        // *not* touch `tests.success` or `tests.wasSkipped` —
        // running tests doesn't determine the outcome, and inventing
        // a pass result would be dishonest: the counterfactual is
        // "you should have run them", not "they would have passed".
        // W_SKIP_TESTS (gated on TEST_RUN_FINISHED) correctly lifts;
        // W_BROKEN (gated on build/test outcomes) is left untouched.
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
