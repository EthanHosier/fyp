package com.github.ethanhosier.analysis.metrics.derived

import com.github.ethanhosier.analysis.divergence.HygieneDetector.COMPOSITE_GAP_MS
import com.github.ethanhosier.analysis.divergence.HygieneDetector.MIN_COMMIT_GAP
import com.github.ethanhosier.analysis.metrics.model.AlternativeTrajectory
import com.github.ethanhosier.analysis.pipeline.CheckpointReport
import com.github.ethanhosier.analysis.pipeline.DivergenceKind
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
 *  7. No churn term. Bigger refactorings shouldn't be inherently worse
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
        //
        // Seed `lastGood` from the user's effective aggregate at
        // `alt.fromSha` so an alt whose first cp is broken inherits
        // the user's last-known-good state (PRIOR) rather than
        // dropping to MIDPOINT. Without this, e.g. a no-op rework alt
        // aliasing a broken user cp had no in-alt prior, so its
        // cleanliness collapsed to session midpoint — wrong, because
        // the natural "before" state is the same user cp the main
        // walk already carry-forwarded for.
        val mainEffectiveBySha: Map<String, Aggregates> = mainCheckpoints
            .mapIndexed { i, cp -> cp.sha to mainAgg[i] }
            .toMap()
        val altAgg = mutableListOf<List<Aggregates>>()
        val altTrustInfo = mutableListOf<List<TrustInfo>>()
        for (alt in alternatives) {
            val rawAgg = alt.altCheckpoints.map { aggregate(it, touchedSet) }
            val effective = MutableList<Aggregates?>(alt.altCheckpoints.size) { null }
            val trust = MutableList(alt.altCheckpoints.size) { TrustInfo(true, null) }
            var lastGood: Aggregates? = mainEffectiveBySha[alt.fromSha]
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

        // Composite-window mapping: groups refactoring steps into
        // batches (≤ 60s gap, Murphy-Hill 2012) and records whether
        // each batch was followed by a `TEST_RUN_FINISHED` event.
        // Drives `W_SKIP_TESTS`'s denominator. Computed once and
        // threaded into both the main and alt walks so they agree
        // on the composite boundaries.
        val compositeInfoByStep = computeCompositeAssignments(refactoringSteps, mainCheckpoints)
        val mainProcess = computeMainProcess(mainCheckpoints, mainCleanliness, refactoringSteps, compositeInfoByStep)
        // Wall-clock durations of the user's main checkpoints — alt
        // steps borrow the slice of the user step they replace so the
        // alt's `brokenMs` denominator stays comparable to the user's.
        val userDurations = computeDurations(mainCheckpoints)
        val altProcess: AltProcessOutput = computeAltProcess(
            alternatives = alternatives,
            altCleanliness = altCleanliness,
            mainSnapshots = mainProcess.snapshots,
            userDurations = userDurations,
            mainCheckpoints = mainCheckpoints,
            refactoringSteps = refactoringSteps,
            compositeInfoByStep = compositeInfoByStep,
        )
        val continuations: List<AltProcessContinuation> = computeAltContinuations(
            alternatives = alternatives,
            altFinalSnapshots = altProcess.finalSnapshots,
            mainCheckpoints = mainCheckpoints,
            mainCleanliness = mainCleanliness,
            refactoringSteps = refactoringSteps,
            userDurations = userDurations,
            compositeInfoByStep = compositeInfoByStep,
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
        /** Count of broken checkpoints — surfaced in the breakdown
         *  detail string alongside the time numerator. */
        val brokenCount: Int,
        /** Milliseconds spent in a broken state (build red OR tests
         *  failed, excluding skipped). Numerator for `brokenFrac`. */
        val brokenMs: Long,
        /** Total milliseconds covered by the walk so far. Denominator
         *  for `brokenFrac`. */
        val elapsedMs: Long,
        val refactoringStepsCount: Int,
        val testsSkippedCount: Int,
        val ideRelevantCount: Int,
        val manualWhenIdeCount: Int,
        /** Number of refactoring composites encountered so far on
         *  this walk — used as the denominator for the `W_SKIP_TESTS`
         *  fraction. A composite is a run of refactoring steps whose
         *  inter-step gap is ≤ [COMPOSITE_GAP_MS] (Murphy-Hill et al.
         *  2012). */
        val compositesCount: Int,
        /** Number of composites that had NO `TEST_RUN_FINISHED` event
         *  between their last step and the next composite's first
         *  step (or session end). Numerator for `W_SKIP_TESTS`. */
        val skippedCompositesCount: Int,
        /** Cumulative count of green-refactor checkpoints since the most
         *  recent (real or synthetic) commit. Mirrors HygieneDetector's
         *  `greenSinceLastCommit` walk: a checkpoint counts iff a
         *  refactoring step landed there AND build+tests both passed
         *  AND tests weren't skipped. Reset to 0 at a user commit or
         *  after a commit-gap event fires. */
        val greenSinceLastCommit: Int,
        /** Number of times the green-refactor counter has crossed
         *  [MIN_COMMIT_GAP] so far on this trajectory — one event per
         *  "you should have committed by now" overdue stretch. Drives
         *  the W_COMMIT_GAP penalty in [buildProcessScore]. */
        val commitGapEvents: Int,
        /** Cumulative `+W_LENGTH` bonus an alt has earned so far. Main
         *  walks stay at 0.0 (no comparator). Alt walks ramp this up
         *  evenly across their N steps to a terminal value of
         *  `W_LENGTH × max(0, (userSteps − altSteps) / userSteps)`,
         *  then the continuation walk inherits the locked-in value. */
        val altLengthBonusPoints: Double,
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
        compositeInfoByStep: Map<Int, CompositeInfo>,
    ): MainProcessOutput {
        if (checkpoints.isEmpty()) return MainProcessOutput(emptyList(), emptyMap())

        val stepsByIndex = stepsByCheckpointIndex(refactoringSteps)
        val cleanliness0 = cleanliness.firstOrNull()?.scalar
        val durations = computeDurations(checkpoints)
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
                durationMs = durations[t],
                compositeInfoByStep = compositeInfoByStep,
            )
            scores += score
            snapshots[checkpoints[t].sha] = next
            snap = next
        }
        return MainProcessOutput(scores = scores, snapshots = snapshots)
    }

    /**
     * Per-checkpoint wall-clock duration: the time slice each cp covers
     * in the user's IDE timeline. cp_i covers `[firstEventTs(cp_i),
     * firstEventTs(cp_{i+1}))`; the last cp's duration is its own event
     * span (mirrors [computeBrokenTime] charging the trailing broken
     * run up to its last event). Drives the time-weighted `W_BROKEN`
     * fraction in [buildProcessScore].
     */
    private fun computeDurations(checkpoints: List<CheckpointReport>): List<Long> {
        fun firstTs(c: CheckpointReport) = c.events.minOfOrNull { it.timestamp }
        fun lastTs(c: CheckpointReport) = c.events.maxOfOrNull { it.timestamp }
        return List(checkpoints.size) { i ->
            val cur = firstTs(checkpoints[i]) ?: return@List 0L
            val nxt = checkpoints.getOrNull(i + 1)?.let { firstTs(it) }
                ?: lastTs(checkpoints[i]) ?: cur
            max(0L, nxt - cur)
        }
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
        brokenMs = 0L,
        elapsedMs = 0L,
        refactoringStepsCount = 0,
        testsSkippedCount = 0,
        ideRelevantCount = 0,
        manualWhenIdeCount = 0,
        compositesCount = 0,
        skippedCompositesCount = 0,
        greenSinceLastCommit = 0,
        commitGapEvents = 0,
        altLengthBonusPoints = 0.0,
        cleanliness0 = cleanliness0,
        checkpointsSoFar = 0,
    )

    /**
     * Walk one user-trajectory step forward from [snap]. Used both for
     * the main pass (seeded with [initialMainSnapshot]) and for alt
     * "continuation" walks past the merge point (seeded with the alt's
     * terminal snapshot — see [computeAltContinuations]). Either way
     * the per-step accounting is the same: real `wasPerformedByIde`
     * flags from [stepsLandingHere] and real `TEST_RUN_FINISHED` events
     * via [stepUserRanTests]. Returns the new snapshot and the
     * cumulative process score after this step.
     */
    private fun advanceMainStep(
        snap: ProcessSnapshot,
        cp: CheckpointReport,
        cpIndex: Int,
        checkpoints: List<CheckpointReport>,
        stepsLandingHere: List<RefactoringStep>,
        cleanT: Double?,
        durationMs: Long,
        compositeInfoByStep: Map<Int, CompositeInfo>,
    ): Pair<ProcessSnapshot, ProcessScore> {
        val build = cp.metrics.build.success
        val testsOk = cp.metrics.tests.success
        val testsSkipped = cp.metrics.tests.wasSkipped
        // Match the frontend's "broken iff build OR tests failed" rule:
        // skipped tests are not a failure.
        val testsFailed = !testsOk && !testsSkipped
        val isBroken = !build || testsFailed
        val brokenInc = if (isBroken) 1 else 0
        val brokenMsInc = if (isBroken) durationMs else 0L

        var refactoringStepsCount = snap.refactoringStepsCount
        var testsSkippedCount = snap.testsSkippedCount
        var ideRelevantCount = snap.ideRelevantCount
        var manualWhenIdeCount = snap.manualWhenIdeCount
        var compositesCount = snap.compositesCount
        var skippedCompositesCount = snap.skippedCompositesCount
        for (s in stepsLandingHere) {
            refactoringStepsCount += 1
            if (!stepUserRanTests(checkpoints, s)) testsSkippedCount += 1
            if (s.refactoring.ideRelevant) {
                ideRelevantCount += 1
                if (!s.wasPerformedByIde) manualWhenIdeCount += 1
            }
            // Composite accounting: only bump the running counts at
            // the *first* step of each composite. A composite that
            // had no `TEST_RUN_FINISHED` event in its post-batch
            // window contributes 1 to the `W_SKIP_TESTS` numerator.
            val info = compositeInfoByStep[s.stepIndex]
            if (info != null && info.isFirstStepInComposite) {
                compositesCount += 1
                if (!info.hasTestInWindow) skippedCompositesCount += 1
            }
        }

        // Commit-gap walk: mirrors HygieneDetector's `greenSinceLastCommit`
        // counter (`anchorIndex` checkpoint logic). Increments per
        // green-refactor checkpoint; resets on real or synthetic commits;
        // each threshold crossing emits one "missed commit" event. The
        // penalty is per overdue stretch, not per step — so a developer
        // who batches 18 green refactors without committing is charged
        // 3 events, not 18 increments.
        val testsAtCpSuccess = testsOk && !testsSkipped
        val landsRefactor = stepsLandingHere.isNotEmpty()
        val greenRefactorCp = landsRefactor && build && testsAtCpSuccess
        var greenSinceLastCommit = snap.greenSinceLastCommit
        var commitGapEvents = snap.commitGapEvents
        when {
            cp.isUserCommit -> greenSinceLastCommit = 0
            greenRefactorCp -> {
                greenSinceLastCommit += 1
                if (greenSinceLastCommit >= MIN_COMMIT_GAP) {
                    commitGapEvents += 1
                    greenSinceLastCommit = 0
                }
            }
        }

        val brokenCount = snap.brokenCount + brokenInc
        val brokenMs = snap.brokenMs + brokenMsInc
        val elapsedMs = snap.elapsedMs + durationMs

        // Main walk doesn't add to the alt-length bonus — it's
        // carried through unchanged so continuation walks (which
        // reuse this function) preserve the alt's locked-in bonus.
        val altLengthBonusPoints = snap.altLengthBonusPoints

        val checkpointsSoFar = snap.checkpointsSoFar + 1
        // Time-weighted broken fraction: avoids dilution from
        // many-small-checkpoints manual-edit clusters.
        val brokenFrac = if (elapsedMs > 0L) brokenMs.toDouble() / elapsedMs.toDouble() else 0.0
        // Laplace smoothing only kicks in when at least one bad batch
        // has happened; a perfect record (0 of N) returns 0 penalty.
        // `skipFrac` is now per-composite (Murphy-Hill 2012 batch
        // boundary); `manualFrac` stays per-step.
        val skipFrac = if (compositesCount == 0 || skippedCompositesCount == 0) 0.0
            else (skippedCompositesCount + 1).toDouble() / (compositesCount + 2).toDouble()
        val manualFrac = if (ideRelevantCount == 0 || manualWhenIdeCount == 0) 0.0
            else (manualWhenIdeCount + 1).toDouble() / (ideRelevantCount + 2).toDouble()

        val cleanlinessGain = if (cleanT == null || snap.cleanliness0 == null) 0.0
            else cleanT - snap.cleanliness0

        val score = buildProcessScore(
            cleanlinessGain = cleanlinessGain,
            brokenFrac = brokenFrac,
            brokenCount = brokenCount,
            brokenMs = brokenMs,
            elapsedMs = elapsedMs,
            checkpointsSoFar = checkpointsSoFar,
            skipFrac = skipFrac,
            skippedCompositesCount = skippedCompositesCount,
            compositesCount = compositesCount,
            manualFrac = manualFrac,
            manualWhenIdeCount = manualWhenIdeCount,
            ideRelevantCount = ideRelevantCount,
            commitGapEvents = commitGapEvents,
            altLengthBonusPoints = altLengthBonusPoints,
        )

        val next = ProcessSnapshot(
            brokenCount = brokenCount,
            brokenMs = brokenMs,
            elapsedMs = elapsedMs,
            refactoringStepsCount = refactoringStepsCount,
            testsSkippedCount = testsSkippedCount,
            ideRelevantCount = ideRelevantCount,
            manualWhenIdeCount = manualWhenIdeCount,
            compositesCount = compositesCount,
            skippedCompositesCount = skippedCompositesCount,
            greenSinceLastCommit = greenSinceLastCommit,
            commitGapEvents = commitGapEvents,
            altLengthBonusPoints = altLengthBonusPoints,
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
        userDurations: List<Long>,
        mainCheckpoints: List<CheckpointReport>,
        refactoringSteps: List<RefactoringStep>,
        compositeInfoByStep: Map<Int, CompositeInfo>,
    ): AltProcessOutput {
        val userIdxBySha = mainCheckpoints.mapIndexed { i, c -> c.sha to i }.toMap()
        val stepByIndex = refactoringSteps.associateBy { it.stepIndex }
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

            val altDurations = altStepDurations(alt, userIdxBySha, stepByIndex, userDurations)
            // W_LENGTH bonus: alt that covers the user's
            // `fromSha → userToSha` window in fewer steps earns
            // `+W_LENGTH × (userSteps − altSteps) / userSteps`.
            // Distributed evenly across the alt's N steps so the
            // chart's alt process-score line ramps smoothly rather
            // than jumping at the merge point. Alts that don't save
            // steps (ORDERING, HYGIENE) end up with 0 per-step bonus.
            val perStepLengthBonus = computeAltLengthBonusPerStep(alt, userIdxBySha)
            // Track composites already counted on THIS alt's walk so
            // collapses (IDE_REPLAY) and revisits (ORDERING duplicates)
            // don't double-bump the composite counter.
            val seenCompositesPerAlt = mutableSetOf<Int>()
            var snap: ProcessSnapshot = anchor
            val perStep = mutableListOf<ProcessScore>()
            for (k in alt.altCheckpoints.indices) {
                val baseDelta = altStepDelta(alt, k, stepByIndex, compositeInfoByStep, seenCompositesPerAlt)
                val delta = baseDelta.copy(lengthBonusPoints = perStepLengthBonus)
                val (next, score) = advanceAltStep(
                    snap = snap,
                    cp = alt.altCheckpoints[k],
                    cleanT = altCleanliness[i][k]?.scalar,
                    delta = delta,
                    durationMs = altDurations.getOrNull(k) ?: 0L,
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
     * Per-alt-step refactoring-step accumulator delta. Mirrors what the
     * main walk would have added at the user step(s) this alt cp
     * replaces — `manualWhenIde` is always zero (alts are mechanically
     * IDE-driven by construction).
     *
     * REWORK: zero — the rework cp isn't a refactoring step, it just
     * removes round-trip code. `landsRefactor = false` so it doesn't
     * contribute to the commit-gap counter either.
     *
     * IDE_REPLAY: cp[0] absorbs ALL the user steps it collapses (the
     * single alt cp stands in for N user refactorings). Subsequent
     * cleanup cps (`altCheckpoints.size > stepIndexes.size`) are
     * residual — zero delta.
     *
     * ORDERING: 1:1, alt step k mirrors user step `stepIndexes[k]`.
     */
    private data class AltStepDelta(
        val refactoringStepsInc: Int,
        val ideRelevantInc: Int,
        val compositesInc: Int,
        val skippedCompositesInc: Int,
        /** Per-step contribution to the alt's `W_LENGTH` bonus.
         *  Computed once per alt as `totalBonus / altCheckpoints.size`
         *  in [computeAltProcess] and applied evenly across the alt's
         *  steps so the alt's process-score line ramps smoothly. */
        val lengthBonusPoints: Double,
        val landsRefactor: Boolean,
    ) {
        companion object {
            val ZERO = AltStepDelta(0, 0, 0, 0, 0.0, false)
        }
    }

    private fun altStepDelta(
        alt: AlternativeTrajectory,
        k: Int,
        stepByIndex: Map<Int, RefactoringStep>,
        compositeInfoByStep: Map<Int, CompositeInfo>,
        seenCompositesPerAlt: MutableSet<Int>,
    ): AltStepDelta {
        val userSteps: List<RefactoringStep> = when (alt.kind) {
            DivergenceKind.REWORK -> emptyList()
            DivergenceKind.IDE_REPLAY ->
                if (k == 0) alt.stepIndexes.mapNotNull { stepByIndex[it] }
                else emptyList()
            // ORDERING / HYGIENE (HYGIENE never actually reaches this
            // walker — pre-baked — but the 1:1 fallback is harmless).
            else -> alt.stepIndexes.getOrNull(k)
                ?.let { stepByIndex[it] }
                ?.let { listOf(it) }
                ?: emptyList()
        }
        if (userSteps.isEmpty()) return AltStepDelta.ZERO

        var ref = 0
        var ide = 0
        var comp = 0
        var skipComp = 0
        for (s in userSteps) {
            ref += 1
            if (s.refactoring.ideRelevant) ide += 1
            // Composite accounting: count each composite once per
            // alt walk (track via the caller-supplied set so the
            // walker doesn't double-count across IDE_REPLAY
            // collapses or ORDERING revisits).
            val info = compositeInfoByStep[s.stepIndex]
            if (info != null && seenCompositesPerAlt.add(info.compositeId)) {
                comp += 1
                // IDE_REPLAY alts are "you could have used the safer
                // mechanical IDE refactor" counterfactuals — assume
                // they include the post-batch test run. ORDERING
                // alts inherit the user's actual batch-test behaviour.
                val altTested = alt.kind == DivergenceKind.IDE_REPLAY || info.hasTestInWindow
                if (!altTested) skipComp += 1
            }
        }
        return AltStepDelta(
            refactoringStepsInc = ref,
            ideRelevantInc = ide,
            compositesInc = comp,
            skippedCompositesInc = skipComp,
            // Filled in by the caller via .copy() — `altStepDelta`
            // computes per-step accumulator deltas; the length bonus
            // is uniform across an alt's steps and known only at the
            // outer loop level.
            lengthBonusPoints = 0.0,
            landsRefactor = true,
        )
    }

    /**
     * Per-alt-step wall-clock duration — what each alt step "borrows"
     * from the user's timeline for its broken-frac denominator.
     *
     * IDE_REPLAY: a single alt cp can collapse multiple user steps,
     * so the alt step covers the full span from `fromSha` to
     * `userToSha`. We charge that whole span to the single alt cp;
     * extra residual cps (when `altCheckpoints.size > stepIndexes.size`)
     * get 0 ms — they're cleanup that happened "for free" in the alt.
     *
     * ORDERING / REWORK / HYGIENE: each alt step matches one user
     * step 1:1, so it borrows that user step's checkpoint duration.
     */
    /**
     * Per-alt-step `W_LENGTH` bonus contribution. Total bonus =
     * `W_LENGTH × max(0, (userSteps − altSteps) / userSteps)` where
     * `userSteps` is the number of user transitions covered by the
     * alt's `[fromSha, userToSha]` window. The total is distributed
     * evenly across the alt's N steps so the cumulative bonus
     * reaches its full value exactly at the alt's terminal step
     * and is then locked in for the continuation walk via the
     * snapshot.
     *
     * Returns 0 for ORDERING (alt length == user length), HYGIENE
     * (fromSha == userToSha, userSteps == 0), no-op REWORK
     * (same case), and any alt that doesn't shorten the path.
     */
    private fun computeAltLengthBonusPerStep(
        alt: AlternativeTrajectory,
        userIdxBySha: Map<String, Int>,
    ): Double {
        val altSteps = alt.altCheckpoints.size
        if (altSteps == 0) return 0.0
        val fromIdx = userIdxBySha[alt.fromSha] ?: return 0.0
        val toIdx = userIdxBySha[alt.userToSha] ?: return 0.0
        val userSteps = toIdx - fromIdx
        if (userSteps <= altSteps) return 0.0
        val totalBonus = W_LENGTH * (userSteps - altSteps).toDouble() / userSteps.toDouble()
        return totalBonus / altSteps.toDouble()
    }

    private fun altStepDurations(
        alt: AlternativeTrajectory,
        userIdxBySha: Map<String, Int>,
        stepByIndex: Map<Int, RefactoringStep>,
        userDurations: List<Long>,
    ): List<Long> {
        val n = alt.altCheckpoints.size
        if (n == 0) return emptyList()
        return when (alt.kind) {
            DivergenceKind.IDE_REPLAY -> {
                val fromIdx = userIdxBySha[alt.fromSha]
                val toIdx = userIdxBySha[alt.userToSha]
                val spanMs = if (fromIdx != null && toIdx != null && fromIdx < toIdx) {
                    // Durations are keyed on the cp the user LEFT — sum
                    // [fromIdx, toIdx) covers the entire transition.
                    userDurations.subList(fromIdx, toIdx).sum()
                } else 0L
                List(n) { k -> if (k == 0) spanMs else 0L }
            }
            DivergenceKind.REWORK -> List(n) { k ->
                // REWORK alts have empty `stepIndexes` (the rework
                // isn't a typed refactor-miner step), so we use the
                // `altCheckpointUserIndexes` map — populated by
                // `computeReworkAlts` to record which user cp each
                // alt step semantically lands at. Same offset
                // convention as ORDERING: `userCpIdx - 1` is the
                // user's transition-into-this-cp duration.
                //
                // Without this, every REWORK alt step contributed 0
                // ms to `elapsedMs`, freezing the alt's brokenFrac at
                // the value it had at `fromSha`. The user's main
                // path would then accumulate more clean time post-
                // fromSha and bring its own brokenFrac down, making
                // the alt look worse than the user even when the
                // alt was genuinely the better path.
                val userCpIdx = alt.altCheckpointUserIndexes.getOrNull(k) ?: return@List 0L
                val srcIdx = (userCpIdx - 1).coerceAtLeast(0)
                userDurations.getOrNull(srcIdx) ?: 0L
            }
            else -> List(n) { k ->
                val userStepIdx = alt.stepIndexes.getOrNull(k) ?: return@List 0L
                val userCpIdx = stepByIndex[userStepIdx]?.toCheckpointIndex
                    ?: return@List 0L
                // The user step "lands" at userCpIdx; the time the user
                // spent in that landing state is durations[userCpIdx-1]
                // (the gap from the prior cp). Use that as the slice
                // the alt step occupies.
                val srcIdx = (userCpIdx - 1).coerceAtLeast(0)
                userDurations.getOrNull(srcIdx) ?: 0L
            }
        }
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
        userDurations: List<Long>,
        compositeInfoByStep: Map<Int, CompositeInfo>,
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
                    durationMs = userDurations.getOrNull(t) ?: 0L,
                    compositeInfoByStep = compositeInfoByStep,
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
     *  metrics and [cleanT]. The refactoring-step accumulators are
     *  driven by [delta] (precomputed in [altStepDelta]) so REWORK
     *  cps (no refactor) contribute 0, and IDE_REPLAY/ORDERING cps
     *  contribute exactly what the user step(s) they replace would
     *  have contributed in the main walk — minus the manual-when-IDE
     *  count, which is always 0 (alts are mechanically IDE-driven).
     *  Returns the new running snapshot and the cumulative process
     *  score after this step. */
    private fun advanceAltStep(
        snap: ProcessSnapshot,
        cp: CheckpointReport,
        cleanT: Double?,
        delta: AltStepDelta,
        durationMs: Long,
    ): Pair<ProcessSnapshot, ProcessScore> {
        val build = cp.metrics.build.success
        val testsOk = cp.metrics.tests.success
        val testsSkipped = cp.metrics.tests.wasSkipped
        val testsFailed = !testsOk && !testsSkipped
        val isBroken = !build || testsFailed
        val brokenInc = if (isBroken) 1 else 0
        // Alt step's "time slice" mirrors the user-step it replaces —
        // see `computeAltProcess` for the lookup. Lets an alt that
        // avoids a long-broken stretch correctly out-score the user.
        val brokenMsInc = if (isBroken) durationMs else 0L

        val brokenCount = snap.brokenCount + brokenInc
        val brokenMs = snap.brokenMs + brokenMsInc
        val elapsedMs = snap.elapsedMs + durationMs

        val refactoringStepsCount = snap.refactoringStepsCount + delta.refactoringStepsInc
        val ideRelevantCount = snap.ideRelevantCount + delta.ideRelevantInc
        // Alts are mechanically IDE-driven ⇒ never count as manual.
        val manualWhenIdeCount = snap.manualWhenIdeCount
        val compositesCount = snap.compositesCount + delta.compositesInc
        val skippedCompositesCount = snap.skippedCompositesCount + delta.skippedCompositesInc
        // testsSkippedCount is no longer used by the score (replaced
        // by skippedCompositesCount) but kept on the snapshot for
        // diagnostics — alt walk just carries the prior value through.
        val testsSkippedCount = snap.testsSkippedCount
        // Accumulate the W_LENGTH bonus — total reaches its locked-in
        // value after the alt's last step; continuation walks inherit
        // it unchanged via the snapshot.
        val altLengthBonusPoints = snap.altLengthBonusPoints + delta.lengthBonusPoints

        // Commit-gap: alt steps land at synthesised commits which carry
        // `isUserCommit = false` by default (HYGIENE COMMIT_GAP alts flip
        // their anchor to `true` — same field, same reset rule). A
        // green-refactor alt step adds to the running counter just like
        // a green user step would; threshold crossings emit events.
        // `landsRefactor` mirrors the main walk's gate — REWORK cps
        // don't actually land a refactor so they're not eligible for
        // the green-refactor count.
        val testsAtAltSuccess = testsOk && !testsSkipped
        val greenRefactorCp = delta.landsRefactor && build && testsAtAltSuccess
        var greenSinceLastCommit = snap.greenSinceLastCommit
        var commitGapEvents = snap.commitGapEvents
        when {
            cp.isUserCommit -> greenSinceLastCommit = 0
            greenRefactorCp -> {
                greenSinceLastCommit += 1
                if (greenSinceLastCommit >= MIN_COMMIT_GAP) {
                    commitGapEvents += 1
                    greenSinceLastCommit = 0
                }
            }
        }

        val checkpointsSoFar = snap.checkpointsSoFar + 1
        val brokenFrac = if (elapsedMs > 0L) brokenMs.toDouble() / elapsedMs.toDouble() else 0.0
        val skipFrac = if (compositesCount == 0 || skippedCompositesCount == 0) 0.0
            else (skippedCompositesCount + 1).toDouble() / (compositesCount + 2).toDouble()
        val manualFrac = if (manualWhenIdeCount == 0) 0.0
            else (manualWhenIdeCount + 1).toDouble() / (ideRelevantCount + 2).toDouble()

        val cleanlinessGain = if (cleanT == null || snap.cleanliness0 == null) 0.0
            else cleanT - snap.cleanliness0

        val score = buildProcessScore(
            cleanlinessGain = cleanlinessGain,
            brokenFrac = brokenFrac,
            brokenCount = brokenCount,
            brokenMs = brokenMs,
            elapsedMs = elapsedMs,
            checkpointsSoFar = checkpointsSoFar,
            skipFrac = skipFrac,
            skippedCompositesCount = skippedCompositesCount,
            compositesCount = compositesCount,
            manualFrac = manualFrac,
            manualWhenIdeCount = manualWhenIdeCount,
            ideRelevantCount = ideRelevantCount,
            commitGapEvents = commitGapEvents,
            altLengthBonusPoints = altLengthBonusPoints,
        )

        val next = ProcessSnapshot(
            brokenCount = brokenCount,
            brokenMs = brokenMs,
            elapsedMs = elapsedMs,
            refactoringStepsCount = refactoringStepsCount,
            testsSkippedCount = testsSkippedCount,
            ideRelevantCount = ideRelevantCount,
            manualWhenIdeCount = manualWhenIdeCount,
            compositesCount = compositesCount,
            skippedCompositesCount = skippedCompositesCount,
            greenSinceLastCommit = greenSinceLastCommit,
            commitGapEvents = commitGapEvents,
            altLengthBonusPoints = altLengthBonusPoints,
            cleanliness0 = snap.cleanliness0,
            checkpointsSoFar = checkpointsSoFar,
        )
        return next to score
    }

    /** True iff the checkpoint that the step landed on has any
     *  `TEST_RUN_FINISHED` event — same heuristic as the frontend. */
    private fun stepUserRanTests(checkpoints: List<CheckpointReport>, s: RefactoringStep): Boolean {
        val cp = checkpoints.getOrNull(s.toCheckpointIndex) ?: return false
        return cp.events.any { it.type == EventType.TEST_RUN_FINISHED }
    }

    /** Per-refactoring-step composite metadata: what composite it
     *  belongs to, whether it's the chronologically-first step of
     *  that composite, and whether the composite has any
     *  `TEST_RUN_FINISHED` event in its test window. */
    private data class CompositeInfo(
        val compositeId: Int,
        val isFirstStepInComposite: Boolean,
        val hasTestInWindow: Boolean,
    )

    /**
     * Group refactoring steps into composites: a step joins the
     * previous composite iff its `timestamp` is within
     * [COMPOSITE_GAP_MS] of the previous step's `timestamp`,
     * otherwise it starts a new one. Following Murphy-Hill, Parnin
     * & Black 2012 (TSE, "How We Refactor and How We Know It"),
     * who report ~40 % of tool-invoked refactorings cluster within
     * 60 s of each other on their Eclipse-telemetry corpus.
     *
     * A composite is "tested" iff any checkpoint event of type
     * [EventType.TEST_RUN_FINISHED] has its timestamp in the
     * half-open interval `[firstStepOfThisComposite.timestamp,
     * firstStepOfNextComposite.timestamp)` — at-or-after the
     * composite's first step, strictly before the next composite
     * begins. For the final composite the upper bound is `+∞`.
     * This rewards the empirically-common pattern of testing once
     * at the batch boundary (and handles synthetic same-timestamp
     * fixtures naturally).
     */
    private fun computeCompositeAssignments(
        refactoringSteps: List<RefactoringStep>,
        checkpoints: List<CheckpointReport>,
    ): Map<Int, CompositeInfo> {
        if (refactoringSteps.isEmpty()) return emptyMap()
        val sorted = refactoringSteps.sortedBy { it.timestamp }

        // Assign each step a composite id + isFirst flag.
        data class Assignment(val compositeId: Int, val isFirst: Boolean)
        val assignments = mutableMapOf<Int, Assignment>()
        var compositeId = 0
        var prevTs: Long? = null
        // For each composite track its lastStep timestamp (closing
        // bound) so we can fill in the test window below.
        val compositeFirstTs = mutableMapOf<Int, Long>()
        for (s in sorted) {
            val previous = prevTs
            val isFirst = previous == null || (s.timestamp - previous) > COMPOSITE_GAP_MS
            if (isFirst && previous != null) compositeId += 1
            assignments[s.stepIndex] = Assignment(compositeId, isFirst)
            compositeFirstTs.putIfAbsent(compositeId, s.timestamp)
            prevTs = s.timestamp
        }

        // Test-run timestamps from all checkpoint events.
        val testTimestamps = checkpoints
            .flatMap { it.events }
            .filter { it.type == EventType.TEST_RUN_FINISHED }
            .map { it.timestamp }
            .sorted()

        // Per-composite hasTest: window is [firstStep, nextFirstStep)
        // (or +∞ for the final composite).
        val maxCompositeId = compositeId
        val hasTest = mutableMapOf<Int, Boolean>()
        for (cid in 0..maxCompositeId) {
            val lo = compositeFirstTs[cid] ?: continue
            val hi = compositeFirstTs[cid + 1] ?: Long.MAX_VALUE
            hasTest[cid] = testTimestamps.any { it >= lo && it < hi }
        }

        return assignments.mapValues { (_, a) ->
            CompositeInfo(
                compositeId = a.compositeId,
                isFirstStepInComposite = a.isFirst,
                hasTestInWindow = hasTest[a.compositeId] ?: false,
            )
        }
    }

    // ---- score assembly + breakdown ----

    private fun buildProcessScore(
        cleanlinessGain: Double,
        brokenFrac: Double,
        brokenCount: Int,
        brokenMs: Long,
        elapsedMs: Long,
        checkpointsSoFar: Int,
        skipFrac: Double,
        skippedCompositesCount: Int,
        compositesCount: Int,
        manualFrac: Double,
        manualWhenIdeCount: Int,
        ideRelevantCount: Int,
        commitGapEvents: Int,
        altLengthBonusPoints: Double,
    ): ProcessScore {
        val cleanlinessPoints = W_GAIN * cleanlinessGain
        val brokenPoints = -W_BROKEN * brokenFrac
        val skipPoints = -W_SKIP_TESTS * skipFrac
        val manualPoints = -W_MANUAL_IDE * manualFrac
        // Fixed −W_COMMIT_GAP per event (one "you should have committed
        // by now" stretch). Unbounded — the score's overall [0, 100]
        // clamp is the only ceiling. Each event = MIN_COMMIT_GAP green
        // refactor checkpoints without a commit.
        val commitGapPoints = -W_COMMIT_GAP * commitGapEvents.toDouble()

        val contributions = listOf(
            ProcessContribution(
                id = "cleanliness",
                label = "Cleanliness gain",
                points = cleanlinessPoints,
                detail = formatGainDetail(cleanlinessGain),
            ),
            ProcessContribution(
                id = "broken",
                label = "Broken time",
                points = brokenPoints,
                detail = if (elapsedMs <= 0L) "no elapsed time yet"
                else "${formatMs(brokenMs)} of ${formatMs(elapsedMs)} broken " +
                    "(${pct(brokenFrac)}) — $brokenCount of $checkpointsSoFar checkpoint" +
                    (if (checkpointsSoFar == 1) "" else "s"),
            ),
            ProcessContribution(
                id = "skipTests",
                label = "Tests skipped after refactor batch",
                points = skipPoints,
                detail = if (compositesCount == 0) "no refactorings yet"
                else "$skippedCompositesCount of $compositesCount refactoring batch${if (compositesCount == 1) "" else "es"} not followed by tests",
            ),
            ProcessContribution(
                id = "manualIde",
                label = "Manual when IDE could refactor",
                points = manualPoints,
                detail = if (ideRelevantCount == 0) "no IDE-relevant refactorings yet"
                else "$manualWhenIdeCount of $ideRelevantCount IDE-relevant step${if (ideRelevantCount == 1) "" else "s"} done manually",
            ),
            ProcessContribution(
                id = "commitGap",
                label = "Long stretch without committing",
                points = commitGapPoints,
                detail = if (commitGapEvents == 0) "no overdue commit stretches"
                else "$commitGapEvents stretch${if (commitGapEvents == 1) "" else "es"} of " +
                    "≥$MIN_COMMIT_GAP green refactor checkpoints without a commit",
            ),
            ProcessContribution(
                id = "altLength",
                label = "Step-savings bonus",
                points = altLengthBonusPoints,
                detail = if (altLengthBonusPoints <= 0.0) "no step savings"
                else "alt covers the user's window in fewer steps " +
                    "(bonus locked in at merge)",
            ),
        )

        val unclamped = BASELINE +
            cleanlinessPoints + brokenPoints +
            skipPoints + manualPoints + commitGapPoints + altLengthBonusPoints
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

    private fun formatGainDetail(gain: Double): String {
        if (gain == 0.0) return "no change from baseline"
        val dir = if (gain > 0.0) "up" else "down"
        return "$dir ${"%.2f".format(kotlin.math.abs(gain))} from c0 baseline"
    }

    private fun pct(x: Double): String = "${(x * 100.0).roundToInt()}%"

    /** Compact ms → "12s" / "4m12s" / "1h04m". For the broken-time
     *  contribution row; matches the order-of-magnitude the user
     *  would say aloud. */
    private fun formatMs(ms: Long): String {
        if (ms <= 0L) return "0s"
        val totalSec = ms / 1000L
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return when {
            h > 0L -> "${h}h${m.toString().padStart(2, '0')}m"
            m > 0L -> "${m}m${s.toString().padStart(2, '0')}s"
            else -> "${s}s"
        }
    }

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
        // Hygiene triplet, ordered by safety-criticality:
        // tests > IDE-correctness > commit cadence.
        private const val W_SKIP_TESTS = 14.0
        private const val W_MANUAL_IDE = 11.0
        private const val W_COMMIT_GAP = 7.0
        // Alt-only step-savings bonus. Sits in the behavioural-cost
        // tier (= W_MANUAL_IDE, "extra labour that could have been
        // avoided"); bounded above by Fowler/Kerievsky's small-steps
        // tradition. Anchored on Mkaouer 2014/2016 + Ouni 2017's
        // "minimise number of refactorings" co-equal SBSE objective.
        // See RESEARCH-metrics-weighting.md C4.
        private const val W_LENGTH = 11.0
        private const val BASELINE = 50
    }
}
