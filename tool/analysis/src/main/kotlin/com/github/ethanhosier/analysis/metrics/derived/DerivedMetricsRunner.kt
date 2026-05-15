package com.github.ethanhosier.analysis.metrics.derived

import com.github.ethanhosier.analysis.metrics.model.AlternativeTrajectory
import com.github.ethanhosier.analysis.pipeline.CheckpointReport
import com.github.ethanhosier.analysis.pipeline.Cleanliness
import com.github.ethanhosier.analysis.pipeline.CleanlinessContribution
import com.github.ethanhosier.analysis.pipeline.DerivedMetrics
import com.github.ethanhosier.analysis.pipeline.ProcessContribution
import com.github.ethanhosier.analysis.pipeline.ProcessScore
import com.github.ethanhosier.analysis.metrics.readability.FileReadability
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
 *  1. Six literature-backed sub-signals aggregated over a fixed
 *     **trajectory-touched file set** (the union of every file changed
 *     across the user's main-trajectory checkpoints). The cleanliness
 *     composite measures the developer's working footprint, not the
 *     codebase at large. Per-sub-signal aggregation:
 *       - Cohesion: mean TCC (Bieman & Kang 1995) over touched classes.
 *       - Coupling: mean CBO (Chidamber & Kemerer 1994) over touched classes.
 *       - Smells: count of PMD violations in touched files.
 *       - Duplication: touched-file rate
 *           = duplicated_lines_in_touched / total_lines_in_touched.
 *       - Readability: line-count-weighted mean of five Buse & Weimer
 *           2010 features over touched files, uniform 0.20 blend.
 *       - Cognitive complexity: mean Campbell 2018 score over touched
 *           methods.
 *     See `RESEARCH-cleanliness-metrics.md` for the per-signal
 *     methodology and citations.
 *
 *  2. Uniform 1/6 weighting across the six sub-signals (Laplace's
 *     principle of insufficient reason — no in-domain calibration
 *     corpus to derive non-uniform weights from). Sensitivity analysis
 *     (`PLAN-experiment.md` Experiment 1) tests the robustness of the
 *     uniform choice.
 *
 *  3. Min-max normalisation across the main trajectory's observed range.
 *     Self-tunes per project / session (a 5-WMC bump on a 0..50 range
 *     is significant; on 0..200 it isn't).
 *
 *  4. Re-base when sub-metrics are missing. If a metric is absent at a
 *     checkpoint (or has degenerate range across the trajectory) its
 *     weight is redistributed proportionally over the remaining ones,
 *     so the composite stays on [0, 1]. The `rebased` flag lets the UI
 *     mark scores that aren't a full six-axis sweep.
 *
 *  5. Alts use main's range and clamp normalised values to [0, 1] so an
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
        /** Process-score continuations past each alt's merge point,
         *  parallel to the `alternatives` argument passed to [run].
         *  Each entry is the recomputed score chain over the user's
         *  remaining checkpoints, anchored at the alt's terminal
         *  cumulative state. Empty list when the alt merges at the
         *  trace end or its anchor snapshot was missing. */
        val continuations: List<AltProcessContinuation> = emptyList(),
        /** Per main-checkpoint trust info, keyed by sha. `Trustworthy`
         *  iff `build.success && tests.success` at that checkpoint
         *  (i.e. the static aggregates came from this checkpoint's
         *  own worktree). Otherwise [TrustInfo.source] flags whether
         *  aggregates were carried from a prior trustworthy checkpoint
         *  ("PRIOR") or filled with the session midpoint when no
         *  prior trustworthy checkpoint existed ("MIDPOINT").
         *  Empty when [run] was called with empty mainCheckpoints. */
        val mainTrust: Map<String, TrustInfo> = emptyMap(),
        /** Per alt-checkpoint trust info, keyed by alt sha. Same shape
         *  and semantics as [mainTrust] — set when an alt checkpoint's
         *  aggregates were carried (because build/tests were broken on
         *  the synthesised tree). */
        val altTrust: Map<String, TrustInfo> = emptyMap(),
    )

    /** Per-checkpoint trust descriptor surfaced on [Result.mainTrust]. */
    data class TrustInfo(
        val trustworthy: Boolean,
        /** Null when trustworthy; "PRIOR" or "MIDPOINT" otherwise. */
        val source: String?,
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
        // Trajectory-touched file set: union of every file path that
        // appeared in any user-trajectory checkpoint's diff, computed
        // once and held fixed for the whole session. Every Layer-1
        // aggregator (cohesion, coupling, smells, duplication,
        // readability, cognitive complexity) operates over this set
        // only — the cleanliness composite measures the quality of
        // the developer's working footprint, not the codebase at
        // large. See RESEARCH-cleanliness-metrics.md §1–§6.
        val touchedSet: Set<String> = mainCheckpoints
            .flatMap { cp -> cp.diff.perFileChurn.map { it.path } }
            .toSet()

        // Raw per-checkpoint aggregates — what CK/PMD/CPD actually
        // produced at this SHA, restricted to [touchedSet]. Used as
        // the input to range computation (filtered to trustworthy
        // only) and as the carry-forward source for the trustworthy
        // slots below.
        val mainRawAgg = mainCheckpoints.map { aggregate(it, touchedSet) }

        // Pass 1: forward carry-forward. Untrustworthy slots inherit
        // the previous trustworthy aggregate. Slots with no prior
        // trustworthy aggregate are left null for pass 2 to fill.
        val mainEffectiveAgg = MutableList<Aggregates?>(mainCheckpoints.size) { null }
        val mainTrustInfo = MutableList(mainCheckpoints.size) { TrustInfo(true, null) }
        run {
            var lastGood: Aggregates? = null
            for (i in mainCheckpoints.indices) {
                if (isTrustworthy(mainCheckpoints[i])) {
                    mainEffectiveAgg[i] = mainRawAgg[i]
                    lastGood = mainRawAgg[i]
                    // mainTrustInfo[i] already set to trustworthy.
                } else if (lastGood != null) {
                    mainEffectiveAgg[i] = lastGood
                    mainTrustInfo[i] = TrustInfo(false, "PRIOR")
                } // else: leave null — pass 2 fills with midpoint.
            }
        }

        // Ranges from trustworthy main checkpoints only — keeps
        // sparse-PMD/CK output from depressing the `lo` floor.
        val ranges = computeRanges(
            mainCheckpoints.indices
                .filter { isTrustworthy(mainCheckpoints[it]) }
                .map { mainRawAgg[it] },
        )

        // Pass 2: fill any remaining null slots (leading untrustworthy
        // gap) with the session midpoint per sub-signal.
        val midpoint = midpointAggregates(ranges)
        for (i in mainEffectiveAgg.indices) {
            if (mainEffectiveAgg[i] == null) {
                mainEffectiveAgg[i] = midpoint
                mainTrustInfo[i] = TrustInfo(false, "MIDPOINT")
            }
        }
        @Suppress("UNCHECKED_CAST")
        val mainAgg = (mainEffectiveAgg as List<Aggregates>)

        // Alt-side two-pass — same shape, per-alt midpoint uses main's
        // range (alts already get clamped normalisation against main's
        // ranges; the midpoint placeholder uses the main ranges too so
        // the alt slot is comparable). Per-alt trust info collected
        // alongside so the pipeline can splice it onto alt CheckpointReports.
        val altAgg = mutableListOf<List<Aggregates>>()
        val altTrustInfo = mutableListOf<List<TrustInfo>>()
        for (alt in alternatives) {
            val rawAgg = alt.altCheckpoints.map { aggregate(it, touchedSet) }
            val effective = MutableList<Aggregates?>(alt.altCheckpoints.size) { null }
            val trust = MutableList(alt.altCheckpoints.size) { TrustInfo(true, null) }
            var lastGood: Aggregates? = null
            for (i in alt.altCheckpoints.indices) {
                if (isTrustworthy(alt.altCheckpoints[i])) {
                    effective[i] = rawAgg[i]
                    lastGood = rawAgg[i]
                } else if (lastGood != null) {
                    effective[i] = lastGood
                    trust[i] = TrustInfo(false, "PRIOR")
                }
            }
            for (i in effective.indices) {
                if (effective[i] == null) {
                    effective[i] = midpoint
                    trust[i] = TrustInfo(false, "MIDPOINT")
                }
            }
            @Suppress("UNCHECKED_CAST")
            altAgg += effective as List<Aggregates>
            altTrustInfo += trust
        }

        val mainCleanliness = mainAgg.map { computeCleanliness(it, ranges, clamp = false) }
        val altCleanliness: List<List<Cleanliness?>> = altAgg.map { perAlt ->
            perAlt.map { computeCleanliness(it, ranges, clamp = true) }
        }

        val mainProcess = computeMainProcess(mainCheckpoints, mainCleanliness, refactoringSteps)
        // Mirror the user's actual test-running habit: each alt step
        // covers the user's `stepIndex` slot, so we credit (or charge)
        // the alt step exactly the same way as the user's corresponding
        // step in `computeMainProcess`. Falls back to `false` for any
        // alt step whose stepIndex doesn't map back to a real user step.
        val userRanTestsByStepIndex: Map<Int, Boolean> = refactoringSteps
            .associate { it.stepIndex to stepUserRanTests(mainCheckpoints, it) }
        val altProcess: AltProcessOutput = computeAltProcess(
            alternatives = alternatives,
            altCleanliness = altCleanliness,
            mainSnapshots = mainProcess.snapshots,
            userRanTestsByStepIndex = userRanTestsByStepIndex,
        )
        val continuations: List<AltProcessContinuation> = computeAltContinuations(
            alternatives = alternatives,
            altFinalSnapshots = altProcess.finalSnapshots,
            mainCheckpoints = mainCheckpoints,
            mainCleanliness = mainCleanliness,
            refactoringSteps = refactoringSteps,
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
                    process = altProcess.perStepScores[i][k],
                )
            }
        }

        val mainTrust = mainCheckpoints.mapIndexed { i, cp -> cp.sha to mainTrustInfo[i] }.toMap()
        val altTrust = LinkedHashMap<String, TrustInfo>()
        for (i in alternatives.indices) {
            val alt = alternatives[i]
            for (k in alt.altCheckpoints.indices) {
                altTrust[alt.altCheckpoints[k].sha] = altTrustInfo[i][k]
            }
        }
        return Result(
            main = mainOut,
            alt = altOut,
            continuations = continuations,
            mainTrust = mainTrust,
            altTrust = altTrust,
        )
    }

    /** A checkpoint's cleanliness aggregates can be trusted iff the
     *  code was demonstrably working there — build green AND tests
     *  green. `TestResult.skipped(...)` writes `success = false` (the
     *  pipeline only skips tests when the build already failed), so
     *  this single check covers both "build broke" and "tests broke /
     *  weren't run because build broke". */
    private fun isTrustworthy(cp: CheckpointReport): Boolean =
        cp.metrics.build.success && cp.metrics.tests.success

    /** Midpoint aggregate `(lo + hi) / 2` per sub-signal — used as a
     *  fallback for untrustworthy checkpoints with no prior trustworthy
     *  baseline to carry forward (build/tests broken from session
     *  start). Degenerate-range cases (no trustworthy checkpoints, or
     *  a single one) collapse to a sensible constant via the existing
     *  range-degenerate handling in [computeCleanliness]. */
    private fun midpointAggregates(ranges: Map<SubMetric, Range>): Aggregates {
        fun mid(m: SubMetric): Double = ranges[m]?.let { (it.lo + it.hi) / 2.0 } ?: 0.0
        return Aggregates(
            coupling = mid(SubMetric.COUPLING),
            // Cohesion is the only sub-metric that's legitimately null
            // when missing — if no trustworthy checkpoint produced a
            // TCC, leave it null (matches today's "no signal" path).
            cohesion = ranges[SubMetric.COHESION]?.let { (it.lo + it.hi) / 2.0 },
            duplication = mid(SubMetric.DUPLICATION),
            readability = mid(SubMetric.READABILITY),
            cognitive = mid(SubMetric.COGNITIVE).toInt(),
            smells = mid(SubMetric.SMELLS).toInt(),
        )
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

    private fun aggregate(cp: CheckpointReport, touchedSet: Set<String>): Aggregates {
        val ck = cp.metrics.ck
        // Mean of CBO over classes whose file lies in the trajectory-
        // touched set. Empty touched-set ⇒ 0.0 fallback (mirrors the
        // legacy "empty list" behaviour so the carry-forward and
        // range-normalisation paths keep working on degenerate inputs).
        val touchedClasses = ck.perClass.filter { it.file in touchedSet }
        val cbos = touchedClasses.map { it.cbo.toDouble() }
        val coupling = if (cbos.isEmpty()) 0.0 else round1(cbos.average())

        // Mean of TCC over touched classes; CK reports null TCC when
        // undefined (< 2 eligible method pairs). Drop those before
        // averaging — including them as 0 would falsely flag trivial
        // classes as incohesive. Cohesion is the only sub-signal that
        // remains genuinely nullable; computeCleanliness redistributes
        // its weight when missing.
        val tccs = touchedClasses.mapNotNull { it.tcc?.toDouble() }
        val cohesion = if (tccs.isEmpty()) null else round2(tccs.average())

        // Touched-file duplication rate:
        //   duplicated_lines_in_touched / total_lines_in_touched.
        // CPD's clone detection still runs codebase-wide so cross-file
        // clones are found; we restrict the numerator (and the
        // denominator) to occurrences/lines inside the touched
        // footprint. Per-file line counts come from the readability
        // runner to avoid a separate filesystem walk.
        val touchedDupLines = cp.metrics.cpd.duplications.sumOf { dup ->
            dup.occurrences.count { it.file in touchedSet } * dup.lines
        }
        val touchedTotalLines = cp.metrics.readability.perFile
            .filter { it.file in touchedSet }
            .sumOf { it.totalLines }
        val duplication = if (touchedTotalLines == 0) 0.0
            else round1(touchedDupLines.toDouble() / touchedTotalLines.toDouble() * 100.0)

        // Per-file readability ratios restricted to the touched set,
        // re-aggregated as a line-count-weighted mean before applying
        // the uniform 0.20 blend across five Buse-Weimer features.
        val readability = readabilityTouched(cp.metrics.readability.perFile, touchedSet)

        val pmd = cp.metrics.pmd
        // Mean of cognitive complexity per method, over methods in
        // touched files. Σ would mechanically increase under
        // Extract Method (more methods × constant per-entry weight);
        // mean correctly rewards the refactoring.
        val cognitiveScores = pmd.methodMetrics.filter { it.file in touchedSet }.map { it.cognitive }
        val cognitive = if (cognitiveScores.isEmpty()) 0 else cognitiveScores.average().roundToInt()
        // Unweighted count of PMD violations in touched files —
        // priority weighting is reserved for the process-score smell
        // ledger to avoid double-counting severity.
        val smells = pmd.violations.count { it.file in touchedSet }

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
     * Uniform 0.20 blend of five Buse-Weimer 2010 features
     * (line length, indentation depth, identifier length, single-
     * letter rate, dictionary-word rate), evaluated on the touched
     * subset of `perFile` and combined with a line-count weighting
     * so a 1000-line file contributes more than a 10-line one.
     * Returns a `[0, 100]` value; degenerates to 0.0 when no touched
     * file exists at this checkpoint.
     *
     * Uniform weights follow Laplace's principle of insufficient
     * reason — no in-domain calibration data distinguishes the five
     * features' relative importance; treat them symmetrically. See
     * RESEARCH-cleanliness-metrics.md §5.
     */
    private fun readabilityTouched(
        perFile: List<FileReadability>,
        touchedSet: Set<String>,
    ): Double {
        val touched = perFile.filter { it.file in touchedSet }
        if (touched.isEmpty()) return 0.0
        val totalLines = touched.sumOf { it.totalLines }.toDouble()
        if (totalLines == 0.0) return 0.0

        fun weighted(extract: (FileReadability) -> Double): Double =
            touched.sumOf { extract(it) * it.totalLines } / totalLines

        val lineLengthScore = 1.0 - min(1.0, weighted { it.avgLineLength } / 100.0)
        val indentationScore = 1.0 - min(1.0, weighted { it.avgIndentation } / 12.0)
        val identifierLengthScore = min(1.0, weighted { it.identifiers.avgLength } / 5.0)
        val singleLetterScore = 1.0 - min(1.0, max(0.0, weighted { it.identifiers.singleLetterRatio }))
        val dictionaryScore = min(1.0, max(0.0, weighted { it.identifiers.dictionaryWordRatio }))
        val blend = 0.20 * (lineLengthScore + indentationScore + identifierLengthScore +
            singleLetterScore + dictionaryScore)
        return round1(blend * 100.0)
    }

    // ---- cleanliness ----

    // Uniform 1/6 weighting across the six sub-signals (encoded as 1.0
    // — computeCleanliness divides by Σ wᵢ so the magnitude is
    // irrelevant). Laplace's principle of insufficient reason: with
    // no in-domain calibration corpus we have no basis to weight one
    // sub-signal above another. See RESEARCH-cleanliness-metrics.md §7.
    private enum class SubMetric(val id: String, val label: String, val weight: Double, val betterLower: Boolean) {
        COGNITIVE("cognitive", "Cognitive complexity", 1.0, betterLower = true),
        COUPLING("coupling", "Coupling", 1.0, betterLower = true),
        DUPLICATION("duplication", "Duplication", 1.0, betterLower = true),
        READABILITY("readability", "Readability", 1.0, betterLower = false),
        SMELLS("smells", "Code smells", 1.0, betterLower = true),
        COHESION("cohesion", "Cohesion", 1.0, betterLower = false),
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
        // Time integral of running open-smell weight: sum_t (added_t -
        // resolved_t) sampled after each checkpoint. Divided by
        // checkpointsSoFar this gives the average open-smell load over
        // the trajectory — the basis for the `netSmell` penalty.
        // Churn-invariant: a path that adds 5 smells then resolves all
        // contributes the same to this integral as a path that adds 5
        // and leaves them open (over the same number of steps).
        val openSmellWeightIntegral: Int,
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

        val stepsByIndex = stepsByCheckpointIndex(refactoringSteps)
        val cleanliness0 = cleanliness.firstOrNull()?.scalar
        var snap = initialMainSnapshot(cleanliness0)

        val scores = mutableListOf<ProcessScore>()
        val snapshots = LinkedHashMap<String, ProcessSnapshot>()
        for (t in checkpoints.indices) {
            val (next, score) = advanceMainStep(
                snap = snap,
                cp = checkpoints[t],
                cpIndex = t,
                checkpoints = checkpoints,
                stepsLandingHere = stepsByIndex[t].orEmpty(),
                cleanT = cleanliness[t]?.scalar,
            )
            scores += score
            snapshots[checkpoints[t].sha] = next
            snap = next
        }
        return MainProcessOutput(scores = scores, snapshots = snapshots)
    }

    /**
     * Steps grouped by their landing checkpoint index — O(1) lookup for
     * any per-checkpoint walk (main pass and continuation walks).
     */
    private fun stepsByCheckpointIndex(
        refactoringSteps: List<RefactoringStep>,
    ): Map<Int, List<RefactoringStep>> {
        val out = HashMap<Int, MutableList<RefactoringStep>>()
        for (s in refactoringSteps) {
            out.getOrPut(s.toCheckpointIndex) { mutableListOf() }.add(s)
        }
        return out
    }

    /** Zero snapshot for the start of a main walk. */
    private fun initialMainSnapshot(cleanliness0: Double?): ProcessSnapshot = ProcessSnapshot(
        brokenCount = 0,
        addedSmellWeight = 0,
        resolvedSmellWeight = 0,
        totalSmellWeightSeen = 0,
        openSmellWeightIntegral = 0,
        refactoringStepsCount = 0,
        testsSkippedCount = 0,
        ideRelevantCount = 0,
        manualWhenIdeCount = 0,
        peakCleanliness = cleanliness0 ?: 0.0,
        dipIntegral = 0.0,
        dipObservations = 0,
        cleanliness0 = cleanliness0,
        checkpointsSoFar = 0,
    )

    /**
     * Walk one user-trajectory step forward from [snap]. Used both for
     * the main pass (seeded with [initialMainSnapshot]) and for alt
     * "continuation" walks past the merge point (seeded with the alt's
     * terminal snapshot — see [computeAltContinuations]). Either way
     * the per-step accounting is the same: real `wasPerformedByIde`
     * flags from [stepsLandingHere], real `TEST_RUN_FINISHED` events
     * via [stepUserRanTests], seed-checkpoint smell handling via
     * [smellsForCheckpoint] keyed on [cpIndex]. Returns the new
     * snapshot and the cumulative process score after this step.
     */
    private fun advanceMainStep(
        snap: ProcessSnapshot,
        cp: CheckpointReport,
        cpIndex: Int,
        checkpoints: List<CheckpointReport>,
        stepsLandingHere: List<RefactoringStep>,
        cleanT: Double?,
    ): Pair<ProcessSnapshot, ProcessScore> {
        val build = cp.metrics.build.success
        val testsOk = cp.metrics.tests.success
        val testsSkipped = cp.metrics.tests.wasSkipped
        // Match the frontend's "broken iff build OR tests failed" rule:
        // skipped tests are not a failure.
        val testsFailed = !testsOk && !testsSkipped
        val brokenInc = if (!build || testsFailed) 1 else 0

        // Smell ledger is priority-weighted ((6 - priority), clamped ≥ 0).
        // Same convention as the frontend `process-score.ts` smell weight.
        // Skip the ledger update entirely when this checkpoint isn't
        // trustworthy — broken build/tests typically means sparse PMD
        // output, which would otherwise look like "every prior smell
        // was resolved here", artificially deflating the smell load.
        val (added, resolved) =
            if (isTrustworthy(cp)) smellsForCheckpoint(checkpoints, cpIndex)
            else 0 to 0
        val addedSmellWeight = snap.addedSmellWeight + added
        val resolvedSmellWeight = snap.resolvedSmellWeight + resolved
        val totalSmellWeightSeen = snap.totalSmellWeightSeen + added
        // Sample running open weight AFTER this checkpoint's adds /
        // resolves are applied. Floor at 0 because a path that's
        // resolved more than it added shouldn't count as negative
        // smell load.
        val openSmellWeightIntegral = snap.openSmellWeightIntegral +
            max(0, addedSmellWeight - resolvedSmellWeight)

        var refactoringStepsCount = snap.refactoringStepsCount
        var testsSkippedCount = snap.testsSkippedCount
        var ideRelevantCount = snap.ideRelevantCount
        var manualWhenIdeCount = snap.manualWhenIdeCount
        for (s in stepsLandingHere) {
            refactoringStepsCount += 1
            if (!stepUserRanTests(checkpoints, s)) testsSkippedCount += 1
            if (s.refactoring.ideRelevant) {
                ideRelevantCount += 1
                if (!s.wasPerformedByIde) manualWhenIdeCount += 1
            }
        }

        val brokenCount = snap.brokenCount + brokenInc

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
        val netSmell = computeNetSmell(openSmellWeightIntegral, checkpointsSoFar)
        // Laplace smoothing only kicks in when at least one bad step
        // has happened; a perfect record (0 of N) returns 0 penalty.
        val skipFrac = if (refactoringStepsCount == 0 || testsSkippedCount == 0) 0.0
            else (testsSkippedCount + 1).toDouble() / (refactoringStepsCount + 2).toDouble()
        val manualFrac = if (ideRelevantCount == 0 || manualWhenIdeCount == 0) 0.0
            else (manualWhenIdeCount + 1).toDouble() / (ideRelevantCount + 2).toDouble()

        val cleanlinessGain = if (cleanT == null || snap.cleanliness0 == null) 0.0
            else cleanT - snap.cleanliness0

        val score = buildProcessScore(
            cleanlinessGain = cleanlinessGain,
            brokenFrac = brokenFrac,
            brokenCount = brokenCount,
            checkpointsSoFar = checkpointsSoFar,
            netSmell = netSmell,
            openSmellWeightIntegral = openSmellWeightIntegral,
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
            openSmellWeightIntegral = openSmellWeightIntegral,
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

    private data class AltProcessOutput(
        val perStepScores: List<List<ProcessScore>>,
        /** One entry per alt, in [alternatives] order. `null` when the
         *  anchor `mainSnapshots[fromSha]` was missing (in which case
         *  the alt's perStepScores are all [ProcessScore.EMPTY] and
         *  there's nothing to continue from). */
        val finalSnapshots: List<ProcessSnapshot?>,
    )

    private fun computeAltProcess(
        alternatives: List<AlternativeTrajectory>,
        altCleanliness: List<List<Cleanliness?>>,
        mainSnapshots: Map<String, ProcessSnapshot>,
        userRanTestsByStepIndex: Map<Int, Boolean>,
    ): AltProcessOutput {
        val perStepScores = mutableListOf<List<ProcessScore>>()
        val finalSnapshots = mutableListOf<ProcessSnapshot?>()
        for (i in alternatives.indices) {
            val alt = alternatives[i]
            // Snapshot at fromSha is "main pass after processing that
            // checkpoint" — the starting state for the alt's first
            // synthetic step. Each subsequent step advances the
            // snapshot in place, so the chain accumulates penalties
            // exactly like the main walk. Missing anchor (shouldn't
            // happen, defensive): emit baselines for every step in
            // the chain.
            val anchor = mainSnapshots[alt.fromSha]
            if (anchor == null) {
                perStepScores += alt.altCheckpoints.map { ProcessScore.EMPTY }
                finalSnapshots += null
                continue
            }

            var snap: ProcessSnapshot = anchor
            val perStep = mutableListOf<ProcessScore>()
            for (k in alt.altCheckpoints.indices) {
                // Mirror the user's actual test-running behaviour at
                // the user step this alt step covers — fair
                // comparison: the user's habit at stepIndex K is what
                // they'd plausibly do had they taken the alt path
                // instead.
                val userStepIndex = alt.stepIndexes.getOrNull(k)
                val userRanTests = userStepIndex
                    ?.let { userRanTestsByStepIndex[it] }
                    ?: false
                val (next, score) = advanceAltStep(
                    snap = snap,
                    cp = alt.altCheckpoints[k],
                    cleanT = altCleanliness[i][k]?.scalar,
                    userRanTests = userRanTests,
                )
                perStep += score
                snap = next
            }
            perStepScores += perStep
            finalSnapshots += snap
        }
        return AltProcessOutput(perStepScores, finalSnapshots)
    }

    /**
     * Roll each alt's terminal cumulative state forward through the
     * user's checkpoints AFTER the alt's `userToSha`, recomputing
     * process scores via [advanceMainStep] (user semantics, not alt
     * semantics — these are the user's actual subsequent steps, with
     * real `wasPerformedByIde` flags and real test-running events).
     *
     * Only process scores are recomputed: all other metrics are
     * point-in-time functions of the code state, which is identical
     * to the user's once the trees converge at the merge point.
     *
     * Returns one [AltProcessContinuation] per alt, in [alternatives]
     * order. The list is empty when the alt's `userToSha` is the last
     * main checkpoint, when its anchor snapshot was missing, or when
     * `userToSha` isn't found in main.
     */
    private fun computeAltContinuations(
        alternatives: List<AlternativeTrajectory>,
        altFinalSnapshots: List<ProcessSnapshot?>,
        mainCheckpoints: List<CheckpointReport>,
        mainCleanliness: List<Cleanliness?>,
        refactoringSteps: List<RefactoringStep>,
    ): List<AltProcessContinuation> {
        if (mainCheckpoints.isEmpty()) return alternatives.map { AltProcessContinuation.EMPTY }

        val checkpointIdxBySha = mainCheckpoints
            .mapIndexed { idx, c -> c.sha to idx }
            .toMap()
        val stepsByIndex = stepsByCheckpointIndex(refactoringSteps)

        return alternatives.mapIndexed { i, alt ->
            val finalSnap = altFinalSnapshots[i] ?: return@mapIndexed AltProcessContinuation.EMPTY
            val mergeIdx = checkpointIdxBySha[alt.userToSha]
                ?: return@mapIndexed AltProcessContinuation.EMPTY
            if (mergeIdx >= mainCheckpoints.lastIndex) return@mapIndexed AltProcessContinuation.EMPTY

            val shas = mutableListOf<String>()
            val scores = mutableListOf<ProcessScore>()
            var snap: ProcessSnapshot = finalSnap
            for (t in (mergeIdx + 1)..mainCheckpoints.lastIndex) {
                val (next, score) = advanceMainStep(
                    snap = snap,
                    cp = mainCheckpoints[t],
                    cpIndex = t,
                    checkpoints = mainCheckpoints,
                    stepsLandingHere = stepsByIndex[t].orEmpty(),
                    cleanT = mainCleanliness[t]?.scalar,
                )
                shas += mainCheckpoints[t].sha
                scores += score
                snap = next
            }
            AltProcessContinuation(checkpointShas = shas, processScores = scores)
        }
    }

    /** Per-alt continuation result: parallel arrays of user-checkpoint
     *  SHAs after the merge point and their recomputed process scores. */
    data class AltProcessContinuation(
        val checkpointShas: List<String>,
        val processScores: List<ProcessScore>,
    ) {
        companion object {
            val EMPTY = AltProcessContinuation(emptyList(), emptyList())
        }
    }

    /** Walk one synthetic alt step forward from [snap] using [cp]'s
     *  metrics + smell delta + [cleanT]. Alt steps are definitionally
     *  IDE-driven (`wasPerformedByIde=true`, `ideRelevant=true`) and
     *  never count as manual. [userRanTests] mirrors the user's actual
     *  TEST_RUN_FINISHED behaviour at the user step this alt step
     *  covers — credits the alt for tests the user would have run had
     *  they taken this path. Returns the new running snapshot and the
     *  cumulative process score after this step. */
    private fun advanceAltStep(
        snap: ProcessSnapshot,
        cp: CheckpointReport,
        cleanT: Double?,
        userRanTests: Boolean,
    ): Pair<ProcessSnapshot, ProcessScore> {
        val build = cp.metrics.build.success
        val testsOk = cp.metrics.tests.success
        val testsSkipped = cp.metrics.tests.wasSkipped
        val testsFailed = !testsOk && !testsSkipped
        val brokenInc = if (!build || testsFailed) 1 else 0

        // Skip the alt smell-ledger update on untrustworthy alt
        // checkpoints — same rationale as the main walk.
        val (addedW, resolvedW) =
            if (isTrustworthy(cp)) smellWeightsForAltCp(cp)
            else 0 to 0
        val brokenCount = snap.brokenCount + brokenInc
        val addedSmellWeight = snap.addedSmellWeight + addedW
        val resolvedSmellWeight = snap.resolvedSmellWeight + resolvedW
        val totalSmellWeightSeen = snap.totalSmellWeightSeen + addedW
        val openSmellWeightIntegral = snap.openSmellWeightIntegral +
            max(0, addedSmellWeight - resolvedSmellWeight)

        val refactoringStepsCount = snap.refactoringStepsCount + 1
        // Synthesised commits carry no user events, so we mirror what
        // the user actually did at the matching stepIndex on their
        // real path — fair comparison rather than a blanket penalty.
        val testsSkippedCount = snap.testsSkippedCount + (if (userRanTests) 0 else 1)
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
        val netSmell = computeNetSmell(openSmellWeightIntegral, checkpointsSoFar)
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
            openSmellWeightIntegral = openSmellWeightIntegral,
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
            openSmellWeightIntegral = openSmellWeightIntegral,
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
        openSmellWeightIntegral: Int,
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
                label = "Smell load (time-average)",
                points = smellPoints,
                detail = formatSmellDetail(
                    openSmellWeightIntegral = openSmellWeightIntegral,
                    checkpointsSoFar = checkpointsSoFar,
                    addedSmellWeight = addedSmellWeight,
                    resolvedSmellWeight = resolvedSmellWeight,
                ),
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

    /**
     * Time-integral smell penalty: `min(1, avgOpenWeight / SATURATION)`.
     * avgOpenWeight = integral of (added - resolved) sampled after each
     * checkpoint, divided by checkpoints seen. Churn-invariant: two paths
     * with the same final unresolved smell count get the same score even
     * if one passed through messier intermediates. Saturates at
     * [SMELL_SATURATION_WEIGHT] open weight (∼ "consistently this much
     * unresolved smell" maxes the penalty).
     */
    private fun computeNetSmell(openSmellWeightIntegral: Int, checkpointsSoFar: Int): Double {
        if (checkpointsSoFar <= 0) return 0.0
        val avg = openSmellWeightIntegral.toDouble() / checkpointsSoFar.toDouble()
        return min(1.0, avg / SMELL_SATURATION_WEIGHT)
    }

    private fun formatSmellDetail(
        openSmellWeightIntegral: Int,
        checkpointsSoFar: Int,
        addedSmellWeight: Int,
        resolvedSmellWeight: Int,
    ): String {
        if (openSmellWeightIntegral == 0 && addedSmellWeight == 0 && resolvedSmellWeight == 0) {
            return "no smells touched"
        }
        val avg = if (checkpointsSoFar <= 0) 0.0
        else openSmellWeightIntegral.toDouble() / checkpointsSoFar.toDouble()
        val openNow = max(0, addedSmellWeight - resolvedSmellWeight)
        return "avg ${"%.1f".format(avg)} open weight per checkpoint (currently $openNow open; " +
            "$addedSmellWeight introduced, $resolvedSmellWeight resolved cumulatively)"
    }

    private fun pct(x: Double): String = "${(x * 100.0).roundToInt()}%"

    // ---- numeric helpers ----

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

        // Average open-smell weight at which the smells penalty saturates
        // (netSmell = 1.0). Weights are priority-derived (6 − priority,
        // clamped ≥ 0), so 10 ≈ "consistently five priority-1 violations
        // open" or "ten priority-5 violations open" across the trajectory.
        private const val SMELL_SATURATION_WEIGHT = 10.0
    }
}
