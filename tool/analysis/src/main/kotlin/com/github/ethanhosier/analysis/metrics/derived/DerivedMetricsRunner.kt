package com.github.ethanhosier.analysis.metrics.derived

import com.github.ethanhosier.analysis.metrics.model.AlternativeTrajectory
import com.github.ethanhosier.analysis.metrics.model.CheckpointReport
import com.github.ethanhosier.analysis.metrics.model.Cleanliness
import com.github.ethanhosier.analysis.metrics.model.CleanlinessContribution
import com.github.ethanhosier.analysis.metrics.model.DerivedMetrics
import com.github.ethanhosier.analysis.metrics.model.ProcessContribution
import com.github.ethanhosier.analysis.metrics.model.ProcessScore
import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Computes the dashboard's derived metrics — per-checkpoint aggregators,
 * cleanliness composite, prefix-cumulative process score — server-side so
 * the report ships them ready to render.
 *
 * Three internal stages:
 *  1. **Aggregators** — pure per-checkpoint reductions over the raw CK /
 *     PMD / CPD / readability blocks.
 *  2. **Cleanliness composite** — min-max normalised across the main
 *     trajectory's range (alts use main's range and clamp to keep main
 *     scores stable when alt extremes appear).
 *  3. **Process score** — prefix-cumulative walk of the main checkpoints
 *     using running counters; alts read the snapshot at their `fromSha`
 *     and advance one synthetic step.
 *
 * No I/O, no parallelism. Output keyed by SHA so [buildAnalysisReport]
 * can splice it onto the matching [CheckpointReport].
 *
 * --- Cleanliness composite design (stage 2) ---
 *
 * Surfaced two ways:
 *   - As its own dashboard metric, with a hover-card breakdown showing
 *     each sub-metric's weight, normalised value, and points contributed.
 *   - As an input to the process score's "cleanliness gain" term.
 *
 *  1. Literature-informed weights, not equal mean. No paper covers this
 *     exact bundle, but the closest references converge on a hierarchy:
 *       cognitive   0.25 — Campbell 2018 (Sonar): strongest empirical
 *                          correlate with comprehension time
 *       coupling    0.20 — Chidamber & Kemerer 1994: coupling repeatedly
 *                          the strongest defect predictor in OO studies
 *       duplication 0.20 — Heitlager et al. 2007 (SIG): one of four
 *                          equal pillars in the maintainability model
 *       readability 0.15 — Buse & Weimer 2010: validated readability
 *                          metric, but smaller effect than structural ones
 *       smells      0.15 — symptom of the above; weighting it as primary
 *                          would risk double-counting
 *       cohesion    0.05 — LCOM family is noisy and contested
 *                          (Etzkorn, Counsell)
 *
 *  2. Min-max normalisation across the main trajectory's observed range.
 *     Self-tunes per project (a 5-WMC bump on a 0..50 project is
 *     significant; on 0..200 it isn't).
 *
 *  3. Re-base when sub-metrics are missing. If a metric is absent at a
 *     checkpoint (or has degenerate range across the trajectory) its
 *     weight is redistributed proportionally over the remaining ones,
 *     so the composite stays on [0, 1]. The `rebased` flag lets the UI
 *     mark scores that aren't a full six-axis sweep.
 *
 *  4. Alts use main's range and clamp normalised values to [0, 1] so an
 *     extreme alt can't shift main's normalisation, while alt scores
 *     still read on the same scale.
 *
 * --- Process score design (stage 3) ---
 *
 * Answers, at each checkpoint t: "is this refactoring trajectory
 * producing cleaner code safely and efficiently, up to here?". Distinct
 * from the six code-quality metrics, which describe state at t; this
 * describes the *process* taken to get there. Decomposable so the detail
 * panel can show which terms moved the score.
 *
 *  1. Prefix-cumulative, not point-in-time. Score(t) judges c0..c_t as a
 *     trajectory, not c_t in isolation. That's what makes it a process
 *     score rather than a quality score.
 *
 *  2. Anchor at 50, not 100. A trajectory that doesn't improve and doesn't
 *     damage anything sits mid-range; pure gain and pure damage are both
 *     visible.
 *
 *  3. Frame as process quality, not absolute code quality. Meaningful
 *     between checkpoints / sessions / user-vs-IDE alts — not as an
 *     absolute "73/100".
 *
 *  4. Bounded (rate-based) penalties with asymmetric Laplace smoothing.
 *     All penalties are fractions in [0, 1] so weights map directly to
 *     point costs ("worst-case manual-IDE costs 11 points"). The
 *     "don't penalise thinking" rule means long thoughtful sessions
 *     shouldn't accumulate penalties just for being long; rates decouple
 *     penalty magnitude from session length. Laplace `(bad+1)/(total+2)`
 *     on the refactoring-step rates avoids the cliff where 1/1 pins to
 *     100%, but is *only* applied when at least one bad step has
 *     occurred — a perfect record (0 bad of N) returns a 0 penalty so
 *     doing the right thing every time isn't punished.
 *
 *  5. No time term. The "stayed broken" intuition is captured by the
 *     count of broken checkpoints, not their duration.
 *
 *  6. Net smells, no separate carry penalty. A `carried` smell is already
 *     on the cumulative ledger from when it was `added`; adding a carry
 *     drag would double-count.
 *
 *  7. Intermediate degradation — running-peak dip integral. Cleanliness
 *     gain is point-in-time (clean(t) − clean(0)) so a trajectory that
 *     dips and then recovers to baseline scores identically to one that
 *     stayed clean — the score forgets the dip. The intermediate-
 *     degradation term remembers it: track the running max of
 *     cleanliness, sum `max(0, peak − clean(i))` across the prefix, and
 *     normalise by checkpoint count to keep the term in [0, 1].
 *     Penalises both the depth of the dip and how long the trajectory
 *     stayed below its prior best. Picked running-peak over endpoint-
 *     floor because the latter lets a clever path hide a dip by ending
 *     where it started; running-peak captures "you got the code clean
 *     once, you don't get to forget that you broke it."
 *
 *  8. No churn term. Bigger refactorings shouldn't be inherently worse
 *     than smaller ones.
 */
class DerivedMetricsRunner {

    data class Result(
        val main: Map<String, DerivedMetrics>,
        val alt: Map<String, DerivedMetrics>,
    )

    /**
     * @param mainCheckpoints user trajectory in chronological order. The
     *   process-score walk relies on this order.
     * @param alternatives synthesised IDE-driven alts; their `altCheckpoint`
     *   is scored against main's running counters at `fromSha`.
     * @param refactoringSteps miner output; both main process score (rate
     *   denominators) and alt process score (`userRanTests` lookup via
     *   `toCheckpointIndex`'s events) read this.
     */
    fun run(
        mainCheckpoints: List<CheckpointReport>,
        alternatives: List<AlternativeTrajectory>,
        refactoringSteps: List<RefactoringStep>,
    ): Result {
        val mainAgg = mainCheckpoints.map { aggregate(it) }
        // List-of-lists: outer parallel to [alternatives], inner parallel
        // to each alt's [altCheckpoints].
        val altAgg: List<List<Aggregates>> = alternatives.map { alt ->
            alt.altCheckpoints.map { aggregate(it) }
        }

        // Ranges from the main trajectory only — alts get the same scale
        // (with clamp on out-of-range values) so a single extreme alt
        // can't move main's normalisation.
        val ranges = computeRanges(mainAgg)

        val mainCleanliness = mainAgg.map { computeCleanliness(it, ranges, clamp = false) }
        val altCleanliness: List<List<Cleanliness?>> = altAgg.map { perAlt ->
            perAlt.map { computeCleanliness(it, ranges, clamp = true) }
        }

        val mainProcess = computeMainProcess(mainCheckpoints, mainCleanliness, refactoringSteps)
        val altProcess: List<List<ProcessScore>> = computeAltProcess(
            alternatives = alternatives,
            altCleanliness = altCleanliness,
            mainSnapshots = mainProcess.snapshots,
        )

        val mainOut = mainCheckpoints.mapIndexed { i, cp ->
            cp.sha to DerivedMetrics(
                coupling = mainAgg[i].coupling,
                cohesion = mainAgg[i].cohesion,
                duplication = mainAgg[i].duplication,
                readability = mainAgg[i].readability,
                cognitive = mainAgg[i].cognitive,
                smells = mainAgg[i].smells,
                cleanliness = mainCleanliness[i],
                process = mainProcess.scores[i],
            )
        }.toMap()

        val altOut = LinkedHashMap<String, DerivedMetrics>()
        for (i in alternatives.indices) {
            val alt = alternatives[i]
            for (k in alt.altCheckpoints.indices) {
                val cp = alt.altCheckpoints[k]
                altOut[cp.sha] = DerivedMetrics(
                    coupling = altAgg[i][k].coupling,
                    cohesion = altAgg[i][k].cohesion,
                    duplication = altAgg[i][k].duplication,
                    readability = altAgg[i][k].readability,
                    cognitive = altAgg[i][k].cognitive,
                    smells = altAgg[i][k].smells,
                    cleanliness = altCleanliness[i][k],
                    process = altProcess[i][k],
                )
            }
        }

        return Result(main = mainOut, alt = altOut)
    }

    // ---- aggregators ----

    private data class Aggregates(
        // Only `cohesion` is nullable — frontend's view-model emits the
        // others unconditionally (with 0 fallbacks for empty inputs), so
        // we mirror that for bit-exact parity on existing fixtures.
        val coupling: Double,
        val cohesion: Double?,
        val duplication: Double,
        val readability: Double,
        val cognitive: Int,
        val smells: Int,
    )

    private fun aggregate(cp: CheckpointReport): Aggregates {
        val ck = cp.metrics.ck
        // P90 of CBO across classes. Always emitted (mirrors frontend) —
        // an empty class list yields 0 via the percentile fallback rather
        // than a null sentinel.
        val coupling = round1(percentile(ck.perClass.map { it.cbo.toDouble() }, 0.9))
        // CK reports null TCC when undefined (< 2 eligible method pairs).
        // Counting those as 0 would falsely flag trivial classes as "no
        // cohesion" — drop them before averaging. This is the only
        // sub-metric the frontend leaves missing when no signal exists,
        // so we mirror that with `null` here.
        val tccs = ck.perClass.mapNotNull { it.tcc?.toDouble() }
        val cohesion = if (tccs.isEmpty()) null else round2(tccs.average())

        // CpdResult always materialises (defaults to EMPTY), so duplication
        // is always emitted — matches the frontend's `if (c.metrics.cpd)`
        // truthy check, which is true for the EMPTY object.
        val duplication = round1(cp.metrics.cpd.duplicatedLinesShare * 100.0)

        // ReadabilitySummary always materialises (defaults to EMPTY), and
        // the frontend's `if (c.metrics.readability?.summary)` is true for
        // the EMPTY object too, so we always emit. The blend over EMPTY
        // ratios degenerates to 0.6 (lineLength + indent + singleLetter
        // pin to 1, the rest to 0) which is the same value the frontend
        // produces.
        val readability = round1(readabilityScore(cp.metrics.readability.summary) * 100.0)

        val pmd = cp.metrics.pmd
        val cognitive = pmd.methodMetrics.sumOf { it.cognitive }
        val smells = pmd.violations.size

        return Aggregates(
            coupling = coupling,
            cohesion = cohesion,
            duplication = duplication,
            readability = readability,
            cognitive = cognitive,
            smells = smells,
        )
    }

    /**
     * 5-signal weighted blend of `readability.summary` returning [0, 1].
     * Comment ratio intentionally excluded — comment density is a poor
     * readability proxy in modern Java. Mirrors the historical frontend
     * formula in `view-model.ts:113-131`.
     */
    private fun readabilityScore(summary: com.github.ethanhosier.analysis.metrics.readability.ReadabilitySummary): Double {
        val lineLengthScore = 1.0 - min(1.0, summary.avgLineLength / 100.0)
        val indentationScore = 1.0 - min(1.0, summary.avgIndentation / 12.0)
        val identifierLengthScore = min(1.0, summary.avgIdentifierLength / 5.0)
        val singleLetterScore = 1.0 - min(1.0, max(0.0, summary.singleLetterRatio))
        val dictionaryScore = min(1.0, max(0.0, summary.dictionaryWordRatio))
        return 0.25 * lineLengthScore +
            0.20 * indentationScore +
            0.20 * identifierLengthScore +
            0.15 * singleLetterScore +
            0.20 * dictionaryScore
    }

    // ---- cleanliness ----

    private enum class SubMetric(val id: String, val label: String, val weight: Double, val betterLower: Boolean) {
        COGNITIVE("cognitive", "Cognitive complexity", 0.25, betterLower = true),
        COUPLING("coupling", "Coupling", 0.20, betterLower = true),
        DUPLICATION("duplication", "Duplication", 0.20, betterLower = true),
        READABILITY("readability", "Readability", 0.15, betterLower = false),
        SMELLS("smells", "Code smells", 0.15, betterLower = true),
        COHESION("cohesion", "Cohesion", 0.05, betterLower = false),
    }

    private fun valueOf(agg: Aggregates, m: SubMetric): Double? = when (m) {
        SubMetric.COGNITIVE -> agg.cognitive.toDouble()
        SubMetric.COUPLING -> agg.coupling
        SubMetric.DUPLICATION -> agg.duplication
        SubMetric.READABILITY -> agg.readability
        SubMetric.SMELLS -> agg.smells.toDouble()
        // Cohesion is the only sub-metric the frontend leaves missing —
        // its weight is redistributed via the rebasing branch below.
        SubMetric.COHESION -> agg.cohesion
    }

    private data class Range(val lo: Double, val hi: Double)

    private fun computeRanges(aggs: List<Aggregates>): Map<SubMetric, Range> {
        val out = mutableMapOf<SubMetric, Range>()
        for (m in SubMetric.entries) {
            var lo = Double.POSITIVE_INFINITY
            var hi = Double.NEGATIVE_INFINITY
            for (a in aggs) {
                val v = valueOf(a, m) ?: continue
                if (v < lo) lo = v
                if (v > hi) hi = v
            }
            if (lo.isFinite() && hi.isFinite()) out[m] = Range(lo, hi)
        }
        return out
    }

    private fun computeCleanliness(
        agg: Aggregates,
        ranges: Map<SubMetric, Range>,
        clamp: Boolean,
    ): Cleanliness? {
        var weighted = 0.0
        var totalW = 0.0
        val rows = mutableListOf<Pair<SubMetric, Triple<Double, Double, Double>>>() // metric -> (normalised, raw, weight)
        var excludedAny = false

        for (m in SubMetric.entries) {
            val raw = valueOf(agg, m)
            val range = ranges[m]
            if (raw == null || range == null || range.hi == range.lo) {
                excludedAny = true
                continue
            }
            var n = (raw - range.lo) / (range.hi - range.lo)
            if (m.betterLower) n = 1.0 - n
            if (clamp) n = max(0.0, min(1.0, n))
            weighted += m.weight * n
            totalW += m.weight
            rows.add(m to Triple(n, raw, m.weight))
        }
        if (totalW == 0.0) return null

        val totalScalar = weighted / totalW
        val displayTotal = (totalScalar * 100.0).roundToInt()
        val contributions = rows.map { (m, t) ->
            val (n, raw, w) = t
            CleanlinessContribution(
                id = m.id,
                label = m.label,
                weight = w,
                normalised = n,
                raw = raw,
                points = (w / totalW) * n * 100.0,
            )
        }
        return Cleanliness(
            score = displayTotal,
            scalar = totalScalar,
            rebased = excludedAny,
            contributions = contributions,
        )
    }

    // ---- process score ----

    private data class ProcessSnapshot(
        val brokenCount: Int,
        val addedSmellWeight: Int,
        val resolvedSmellWeight: Int,
        val totalSmellWeightSeen: Int,
        val refactoringStepsCount: Int,
        val testsSkippedCount: Int,
        val ideRelevantCount: Int,
        val manualWhenIdeCount: Int,
        val peakCleanliness: Double,
        val dipIntegral: Double,
        val dipObservations: Int,
        val cleanliness0: Double?,
        val checkpointsSoFar: Int,
    )

    private data class MainProcessOutput(
        val scores: List<ProcessScore>,
        // Snapshot indexed by main-checkpoint sha — captures running
        // counters AFTER processing that checkpoint, which is exactly the
        // state an alt branching from that sha should start from.
        val snapshots: Map<String, ProcessSnapshot>,
    )

    private fun computeMainProcess(
        checkpoints: List<CheckpointReport>,
        cleanliness: List<Cleanliness?>,
        refactoringSteps: List<RefactoringStep>,
    ): MainProcessOutput {
        if (checkpoints.isEmpty()) return MainProcessOutput(emptyList(), emptyMap())

        // Steps grouped by their landing checkpoint index — O(1) lookup
        // inside the per-checkpoint loop.
        val stepsByIndex = HashMap<Int, MutableList<RefactoringStep>>()
        for (s in refactoringSteps) {
            stepsByIndex.getOrPut(s.toCheckpointIndex) { mutableListOf() }.add(s)
        }

        val cleanliness0 = cleanliness.firstOrNull()?.scalar

        var brokenCount = 0
        var addedSmellWeight = 0
        var resolvedSmellWeight = 0
        var totalSmellWeightSeen = 0
        var refactoringStepsCount = 0
        var testsSkippedCount = 0
        var ideRelevantCount = 0
        var manualWhenIdeCount = 0
        var peakCleanliness = cleanliness0 ?: 0.0
        var dipIntegral = 0.0
        var dipObservations = 0

        val scores = mutableListOf<ProcessScore>()
        val snapshots = LinkedHashMap<String, ProcessSnapshot>()

        for (t in checkpoints.indices) {
            val cp = checkpoints[t]
            val build = cp.metrics.build.success
            val testsOk = cp.metrics.tests.success
            val testsSkipped = cp.metrics.tests.wasSkipped
            // Match the frontend's "broken iff build OR tests failed" rule:
            // skipped tests are not a failure.
            val testsFailed = !testsOk && !testsSkipped
            if (!build || testsFailed) brokenCount += 1

            // Smell ledger is priority-weighted ((6 - priority), clamped ≥ 0).
            // Same convention as the frontend `process-score.ts` smell weight.
            val (added, resolved) = smellsForCheckpoint(checkpoints, t)
            addedSmellWeight += added
            resolvedSmellWeight += resolved
            totalSmellWeightSeen += added

            for (s in stepsByIndex[t].orEmpty()) {
                refactoringStepsCount += 1
                if (!stepUserRanTests(checkpoints, s)) testsSkippedCount += 1
                if (s.refactoring.ideRelevant) {
                    ideRelevantCount += 1
                    if (!s.wasPerformedByIde) manualWhenIdeCount += 1
                }
            }

            val cleanT = cleanliness[t]?.scalar
            val cleanlinessGain = if (cleanT == null || cleanliness0 == null) 0.0 else cleanT - cleanliness0

            if (cleanT != null) {
                if (cleanT > peakCleanliness) peakCleanliness = cleanT
                dipIntegral += max(0.0, peakCleanliness - cleanT)
                dipObservations += 1
            }
            val degradationFrac = if (dipObservations == 0) 0.0 else dipIntegral / dipObservations
            val brokenFrac = brokenCount.toDouble() / (t + 1).toDouble()
            val netSmell = if (totalSmellWeightSeen == 0) 0.0
                else max(0, addedSmellWeight - resolvedSmellWeight).toDouble() / totalSmellWeightSeen.toDouble()
            // Laplace smoothing only kicks in when at least one bad step
            // has happened; a perfect record (0 of N) returns 0 penalty.
            val skipFrac = if (refactoringStepsCount == 0 || testsSkippedCount == 0) 0.0
                else (testsSkippedCount + 1).toDouble() / (refactoringStepsCount + 2).toDouble()
            val manualFrac = if (ideRelevantCount == 0 || manualWhenIdeCount == 0) 0.0
                else (manualWhenIdeCount + 1).toDouble() / (ideRelevantCount + 2).toDouble()

            val score = buildProcessScore(
                cleanlinessGain = cleanlinessGain,
                brokenFrac = brokenFrac,
                brokenCount = brokenCount,
                checkpointsSoFar = t + 1,
                netSmell = netSmell,
                addedSmellWeight = addedSmellWeight,
                resolvedSmellWeight = resolvedSmellWeight,
                skipFrac = skipFrac,
                testsSkippedCount = testsSkippedCount,
                refactoringStepsCount = refactoringStepsCount,
                manualFrac = manualFrac,
                manualWhenIdeCount = manualWhenIdeCount,
                ideRelevantCount = ideRelevantCount,
                degradationFrac = degradationFrac,
                peakCleanliness = peakCleanliness,
                cleanT = cleanT,
            )
            scores.add(score)

            snapshots[cp.sha] = ProcessSnapshot(
                brokenCount = brokenCount,
                addedSmellWeight = addedSmellWeight,
                resolvedSmellWeight = resolvedSmellWeight,
                totalSmellWeightSeen = totalSmellWeightSeen,
                refactoringStepsCount = refactoringStepsCount,
                testsSkippedCount = testsSkippedCount,
                ideRelevantCount = ideRelevantCount,
                manualWhenIdeCount = manualWhenIdeCount,
                peakCleanliness = peakCleanliness,
                dipIntegral = dipIntegral,
                dipObservations = dipObservations,
                cleanliness0 = cleanliness0,
                checkpointsSoFar = t + 1,
            )
        }

        return MainProcessOutput(scores = scores, snapshots = snapshots)
    }

    private fun computeAltProcess(
        alternatives: List<AlternativeTrajectory>,
        altCleanliness: List<List<Cleanliness?>>,
        mainSnapshots: Map<String, ProcessSnapshot>,
    ): List<List<ProcessScore>> = alternatives.mapIndexed { i, alt ->
        // Snapshot at fromSha is "main pass after processing that
        // checkpoint" — the starting state for the alt's first synthetic
        // step. Each subsequent step advances the snapshot in place, so
        // the chain accumulates penalties exactly like the main walk.
        // Missing anchor (shouldn't happen, defensive): emit baselines
        // for every step in the chain.
        val anchor = mainSnapshots[alt.fromSha]
            ?: return@mapIndexed alt.altCheckpoints.map { ProcessScore.EMPTY }

        var snap = anchor
        val perStep = mutableListOf<ProcessScore>()
        for (k in alt.altCheckpoints.indices) {
            val (next, score) = advanceAltStep(snap, alt.altCheckpoints[k], altCleanliness[i][k]?.scalar)
            perStep += score
            snap = next
        }
        perStep
    }

    /** Walk one synthetic alt step forward from [snap] using [cp]'s
     *  metrics + smell delta + [cleanT]. Alt steps are definitionally
     *  IDE-driven (`wasPerformedByIde=true`, `ideRelevant=true`) and
     *  carry no user events ⇒ tests skipped, never manual. Returns the
     *  new running snapshot and the cumulative process score after this
     *  step. */
    private fun advanceAltStep(
        snap: ProcessSnapshot,
        cp: CheckpointReport,
        cleanT: Double?,
    ): Pair<ProcessSnapshot, ProcessScore> {
        val build = cp.metrics.build.success
        val testsOk = cp.metrics.tests.success
        val testsSkipped = cp.metrics.tests.wasSkipped
        val testsFailed = !testsOk && !testsSkipped
        val brokenInc = if (!build || testsFailed) 1 else 0

        val (addedW, resolvedW) = smellWeightsForAltCp(cp)
        val brokenCount = snap.brokenCount + brokenInc
        val addedSmellWeight = snap.addedSmellWeight + addedW
        val resolvedSmellWeight = snap.resolvedSmellWeight + resolvedW
        val totalSmellWeightSeen = snap.totalSmellWeightSeen + addedW

        val refactoringStepsCount = snap.refactoringStepsCount + 1
        // No user events on a synthesised commit ⇒ tests not run.
        val testsSkippedCount = snap.testsSkippedCount + 1
        val ideRelevantCount = snap.ideRelevantCount + 1
        // Performed by IDE definitionally ⇒ doesn't count as manual.
        val manualWhenIdeCount = snap.manualWhenIdeCount

        var peakCleanliness = snap.peakCleanliness
        var dipIntegral = snap.dipIntegral
        var dipObservations = snap.dipObservations
        if (cleanT != null) {
            if (cleanT > peakCleanliness) peakCleanliness = cleanT
            dipIntegral += max(0.0, peakCleanliness - cleanT)
            dipObservations += 1
        }
        val degradationFrac = if (dipObservations == 0) 0.0 else dipIntegral / dipObservations

        val checkpointsSoFar = snap.checkpointsSoFar + 1
        val brokenFrac = brokenCount.toDouble() / checkpointsSoFar.toDouble()
        val netSmell = if (totalSmellWeightSeen == 0) 0.0
            else max(0, addedSmellWeight - resolvedSmellWeight).toDouble() / totalSmellWeightSeen.toDouble()
        val skipFrac = if (testsSkippedCount == 0) 0.0
            else (testsSkippedCount + 1).toDouble() / (refactoringStepsCount + 2).toDouble()
        val manualFrac = if (manualWhenIdeCount == 0) 0.0
            else (manualWhenIdeCount + 1).toDouble() / (ideRelevantCount + 2).toDouble()

        val cleanlinessGain = if (cleanT == null || snap.cleanliness0 == null) 0.0
            else cleanT - snap.cleanliness0

        val score = buildProcessScore(
            cleanlinessGain = cleanlinessGain,
            brokenFrac = brokenFrac,
            brokenCount = brokenCount,
            checkpointsSoFar = checkpointsSoFar,
            netSmell = netSmell,
            addedSmellWeight = addedSmellWeight,
            resolvedSmellWeight = resolvedSmellWeight,
            skipFrac = skipFrac,
            testsSkippedCount = testsSkippedCount,
            refactoringStepsCount = refactoringStepsCount,
            manualFrac = manualFrac,
            manualWhenIdeCount = manualWhenIdeCount,
            ideRelevantCount = ideRelevantCount,
            degradationFrac = degradationFrac,
            peakCleanliness = peakCleanliness,
            cleanT = cleanT,
        )

        val next = ProcessSnapshot(
            brokenCount = brokenCount,
            addedSmellWeight = addedSmellWeight,
            resolvedSmellWeight = resolvedSmellWeight,
            totalSmellWeightSeen = totalSmellWeightSeen,
            refactoringStepsCount = refactoringStepsCount,
            testsSkippedCount = testsSkippedCount,
            ideRelevantCount = ideRelevantCount,
            manualWhenIdeCount = manualWhenIdeCount,
            peakCleanliness = peakCleanliness,
            dipIntegral = dipIntegral,
            dipObservations = dipObservations,
            cleanliness0 = snap.cleanliness0,
            checkpointsSoFar = checkpointsSoFar,
        )
        return next to score
    }

    /**
     * Added / resolved priority-weight totals at main checkpoint index `t`.
     * Mirrors the frontend `deriveSmells` bucketing rule: the seed
     * checkpoint (t == 0) has no predecessor to carry from, so PMD's
     * `firstSeenAtSha = curr.sha` rows are treated as carried — *not*
     * added — to avoid charging the user for preexisting baseline smells.
     */
    private fun smellsForCheckpoint(checkpoints: List<CheckpointReport>, t: Int): Pair<Int, Int> {
        val cp = checkpoints[t]
        val violations = cp.metrics.pmd.violations
        val firstSeen = cp.pmdTracking.firstSeenAtSha

        var added = 0
        if (t > 0) {
            for (i in violations.indices) {
                val seenAt = firstSeen.getOrNull(i) ?: cp.sha
                if (seenAt == cp.sha) added += smellWeight(violations[i].priority)
            }
        }
        var resolved = 0
        for (r in cp.pmdTracking.resolvedSincePrev) resolved += smellWeight(r.priority)
        return added to resolved
    }

    /** Same convention for an alt checkpoint — seed semantics don't apply
     *  (an alt is always a transition from `fromSha`), so any violation
     *  whose `firstSeenAtSha` is the alt sha counts as added. */
    private fun smellWeightsForAltCp(cp: CheckpointReport): Pair<Int, Int> {
        val violations = cp.metrics.pmd.violations
        val firstSeen = cp.pmdTracking.firstSeenAtSha
        var added = 0
        for (i in violations.indices) {
            val seenAt = firstSeen.getOrNull(i) ?: cp.sha
            if (seenAt == cp.sha) added += smellWeight(violations[i].priority)
        }
        var resolved = 0
        for (r in cp.pmdTracking.resolvedSincePrev) resolved += smellWeight(r.priority)
        return added to resolved
    }

    /** Priority 1 (worst) → 5; priority 5 → 1. Clamped ≥ 0. */
    private fun smellWeight(priority: Int): Int = max(0, 6 - priority)

    /** True iff the checkpoint that the step landed on has any
     *  `TEST_RUN_FINISHED` event — same heuristic as the frontend. */
    private fun stepUserRanTests(checkpoints: List<CheckpointReport>, s: RefactoringStep): Boolean {
        val cp = checkpoints.getOrNull(s.toCheckpointIndex) ?: return false
        return cp.events.any { it.type == EventType.TEST_RUN_FINISHED }
    }

    // ---- score assembly + breakdown ----

    private fun buildProcessScore(
        cleanlinessGain: Double,
        brokenFrac: Double,
        brokenCount: Int,
        checkpointsSoFar: Int,
        netSmell: Double,
        addedSmellWeight: Int,
        resolvedSmellWeight: Int,
        skipFrac: Double,
        testsSkippedCount: Int,
        refactoringStepsCount: Int,
        manualFrac: Double,
        manualWhenIdeCount: Int,
        ideRelevantCount: Int,
        degradationFrac: Double,
        peakCleanliness: Double,
        cleanT: Double?,
    ): ProcessScore {
        val cleanlinessPoints = W_GAIN * cleanlinessGain
        val brokenPoints = -W_BROKEN * brokenFrac
        val smellPoints = -W_SMELL * netSmell
        val degradationPoints = -W_DEGRADATION * degradationFrac
        val skipPoints = -W_SKIP_TESTS * skipFrac
        val manualPoints = -W_MANUAL_IDE * manualFrac

        val contributions = listOf(
            ProcessContribution(
                id = "cleanliness",
                label = "Cleanliness gain",
                points = cleanlinessPoints,
                detail = formatGainDetail(cleanlinessGain),
            ),
            ProcessContribution(
                id = "degradation",
                label = "Intermediate degradation",
                points = degradationPoints,
                detail = formatDegradationDetail(degradationFrac, peakCleanliness, cleanT),
            ),
            ProcessContribution(
                id = "broken",
                label = "Broken checkpoints",
                points = brokenPoints,
                detail = "$brokenCount of $checkpointsSoFar checkpoint${if (checkpointsSoFar == 1) "" else "s"} broken (${pct(brokenFrac)})",
            ),
            ProcessContribution(
                id = "smells",
                label = "Smells introduced (net)",
                points = smellPoints,
                detail = formatSmellDetail(addedSmellWeight, resolvedSmellWeight),
            ),
            ProcessContribution(
                id = "skipTests",
                label = "Tests skipped after refactor",
                points = skipPoints,
                detail = if (refactoringStepsCount == 0) "no refactorings yet"
                else "$testsSkippedCount of $refactoringStepsCount refactoring step${if (refactoringStepsCount == 1) "" else "s"} not followed by tests",
            ),
            ProcessContribution(
                id = "manualIde",
                label = "Manual when IDE could refactor",
                points = manualPoints,
                detail = if (ideRelevantCount == 0) "no IDE-relevant refactorings yet"
                else "$manualWhenIdeCount of $ideRelevantCount IDE-relevant step${if (ideRelevantCount == 1) "" else "s"} done manually",
            ),
        )

        val unclamped = BASELINE +
            cleanlinessPoints + brokenPoints + smellPoints +
            degradationPoints + skipPoints + manualPoints
        val clampedValue = max(0.0, min(100.0, unclamped))
        val total = clampedValue.roundToLong().toInt()
        val clamped = unclamped != clampedValue
        return ProcessScore(
            total = total,
            baseline = BASELINE,
            clamped = clamped,
            contributions = contributions,
        )
    }

    private fun formatDegradationDetail(frac: Double, peak: Double, cleanT: Double?): String {
        if (frac == 0.0) return "no dip below running peak"
        val gap = if (cleanT == null) 0.0 else max(0.0, peak - cleanT)
        return "avg gap ${"%.2f".format(frac)} below running peak ${"%.2f".format(peak)} (currently ${"%.2f".format(gap)} below)"
    }

    private fun formatGainDetail(gain: Double): String {
        if (gain == 0.0) return "no change from baseline"
        val dir = if (gain > 0.0) "up" else "down"
        return "$dir ${"%.2f".format(kotlin.math.abs(gain))} from c0 baseline"
    }

    private fun formatSmellDetail(added: Int, resolved: Int): String {
        if (added == 0 && resolved == 0) return "no smells touched"
        if (added == 0) return "$resolved weight resolved, none introduced"
        if (resolved == 0) return "$added weight introduced, none resolved"
        return "$added weight introduced, $resolved resolved (net ${added - resolved})"
    }

    private fun pct(x: Double): String = "${(x * 100.0).roundToInt()}%"

    // ---- numeric helpers ----

    /**
     * Value at the given percentile (0..1), nearest-rank. Bit-exact match
     * for the frontend `percentile` helper in `view-model.ts:95`:
     * `idx = min(n-1, floor(p * n))`. Notably differs from the more
     * common `ceil(p*n)-1` formulation at boundary cases (p·n integer).
     */
    private fun percentile(xs: List<Double>, p: Double): Double {
        if (xs.isEmpty()) return 0.0
        val sorted = xs.sorted()
        val idx = min(sorted.size - 1, kotlin.math.floor(p * sorted.size).toInt())
        return sorted[idx]
    }

    private fun round1(x: Double): Double = (x * 10.0).roundToLong() / 10.0
    private fun round2(x: Double): Double = (x * 100.0).roundToLong() / 100.0

    companion object {
        // Top-level term weights. The score is `50 + gain·W_GAIN - Σ
        // penalty·W`, clamped to [0, 100]. Weights chosen so a flawless
        // trajectory with maximum cleanliness gain reaches exactly 100
        // (`BASELINE + W_GAIN`), and a maximally damaging one bottoms out
        // below 0 (clamped). Internal proportions follow the original
        // 35 / 20 / 15 / 15 / 10 / 8 design, scaled by 50/35 ≈ 1.43 and
        // rounded — the score's meaning is unchanged, the scale just
        // spans the full 0..100 range now.
        //
        // Worst-case unclamped: 50 - 95 = -45, so genuinely bad sessions
        // still hit the 0 floor; clamping is a safety net, not a routine
        // event.
        private const val W_GAIN = 50.0
        private const val W_BROKEN = 28.0
        // Same magnitude as the smells weight: both are "you let things
        // deteriorate" penalties. Lighter than broken-checkpoints (more
        // acute) and the gain term (which still dominates net improvement).
        private const val W_DEGRADATION = 21.0
        private const val W_SMELL = 21.0
        private const val W_SKIP_TESTS = 14.0
        private const val W_MANUAL_IDE = 11.0
        private const val BASELINE = 50
    }
}
