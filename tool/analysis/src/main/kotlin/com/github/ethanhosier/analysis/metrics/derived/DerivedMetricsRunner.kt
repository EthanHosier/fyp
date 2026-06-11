package com.github.ethanhosier.analysis.metrics.derived

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

class DerivedMetricsRunner(
    val config: ScoringConfig = ScoringConfig.PRODUCTION,
) {
    val weights: ProcessScoreWeights get() = config.process
    private val cleanlinessWeights: CleanlinessWeights get() = config.cleanliness


    data class Result(
        val main: Map<String, DerivedMetrics>,
        val alt: Map<String, DerivedMetrics>,
        val continuations: List<AltProcessContinuation> = emptyList(),
        val mainTrust: Map<String, TrustInfo> = emptyMap(),
        val altTrust: Map<String, TrustInfo> = emptyMap(),
    )

    data class TrustInfo(
        val trustworthy: Boolean,
        val source: String?,
    )

    fun run(
        mainCheckpoints: List<CheckpointReport>,
        alternatives: List<AlternativeTrajectory>,
        refactoringSteps: List<RefactoringStep>,
    ): Result {
        val touchedSet: Set<String> = mainCheckpoints
            .flatMap { cp -> cp.diff.perFileChurn.map { it.path } }
            .toSet()

        val mainRawAgg = mainCheckpoints.map { aggregate(it, touchedSet) }

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

        val compositeInfoByStep = computeCompositeAssignments(refactoringSteps, mainCheckpoints)
        val mainProcess = computeMainProcess(mainCheckpoints, mainCleanliness, refactoringSteps, compositeInfoByStep)
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

    private fun isTrustworthy(cp: CheckpointReport): Boolean =
        cp.metrics.build.success && cp.metrics.tests.success

    private fun midpointAggregates(ranges: Map<SubMetric, Range>): Aggregates {
        fun mid(m: SubMetric): Double = ranges[m]?.let { (it.lo + it.hi) / 2.0 } ?: 0.0
        return Aggregates(
            coupling = mid(SubMetric.COUPLING),
            cohesion = ranges[SubMetric.COHESION]?.let { (it.lo + it.hi) / 2.0 },
            duplication = mid(SubMetric.DUPLICATION),
            readability = mid(SubMetric.READABILITY),
            cognitive = mid(SubMetric.COGNITIVE).toInt(),
            smells = mid(SubMetric.SMELLS).toInt(),
        )
    }

    // ---- aggregators ----

    private data class Aggregates(
        val coupling: Double,
        val cohesion: Double?,
        val duplication: Double,
        val readability: Double,
        val cognitive: Int,
        val smells: Int,
    )

    private fun aggregate(cp: CheckpointReport, touchedSet: Set<String>): Aggregates {
        val ck = cp.metrics.ck
        val touchedClasses = ck.perClass.filter { it.file in touchedSet }
        val cbos = touchedClasses.map { it.cbo.toDouble() }
        val coupling = if (cbos.isEmpty()) 0.0 else round1(cbos.average())

        val tccs = touchedClasses.mapNotNull { it.tcc?.toDouble() }
        val cohesion = if (tccs.isEmpty()) null else round2(tccs.average())

        val touchedDupLines = cp.metrics.cpd.duplications.sumOf { dup ->
            dup.occurrences.count { it.file in touchedSet } * dup.lines
        }
        val touchedTotalLines = cp.metrics.readability.perFile
            .filter { it.file in touchedSet }
            .sumOf { it.totalLines }
        val duplication = if (touchedTotalLines == 0) 0.0
            else round1(touchedDupLines.toDouble() / touchedTotalLines.toDouble() * 100.0)

        val readability = readabilityTouched(cp.metrics.readability.perFile, touchedSet)

        val pmd = cp.metrics.pmd
        val cognitiveScores = pmd.methodMetrics.filter { it.file in touchedSet }.map { it.cognitive }
        val cognitive = if (cognitiveScores.isEmpty()) 0 else cognitiveScores.average().roundToInt()
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

    private enum class SubMetric(val id: String, val label: String, val betterLower: Boolean) {
        COGNITIVE("cognitive", "Cognitive complexity", betterLower = true),
        COUPLING("coupling", "Coupling", betterLower = true),
        DUPLICATION("duplication", "Duplication", betterLower = true),
        READABILITY("readability", "Readability", betterLower = false),
        SMELLS("smells", "Code smells", betterLower = true),
        COHESION("cohesion", "Cohesion", betterLower = false),
    }

    private fun weightOf(m: SubMetric): Double = when (m) {
        SubMetric.COGNITIVE -> cleanlinessWeights.cognitive
        SubMetric.COUPLING -> cleanlinessWeights.coupling
        SubMetric.DUPLICATION -> cleanlinessWeights.duplication
        SubMetric.READABILITY -> cleanlinessWeights.readability
        SubMetric.SMELLS -> cleanlinessWeights.smells
        SubMetric.COHESION -> cleanlinessWeights.cohesion
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
            val w = weightOf(m)
            weighted += w * n
            totalW += w
            rows.add(m to Triple(n, raw, w))
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
        val brokenMs: Long,
        val elapsedMs: Long,
        val refactoringStepsCount: Int,
        val testsSkippedCount: Int,
        val ideRelevantCount: Int,
        val manualWhenIdeCount: Int,
        val compositesCount: Int,
        val skippedCompositesCount: Int,
        val greenSinceLastCommit: Int,
        val commitGapEvents: Int,
        val altLengthBonusPoints: Double,
        val cleanliness0: Double?,
        val cleanlinessFinal: Double?,
        val lagSum: Double,
        val checkpointsSoFar: Int,
    )

    private data class MainProcessOutput(
        val scores: List<ProcessScore>,
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
        val cleanlinessFinal = cleanliness.lastOrNull()?.scalar
        val durations = computeDurations(checkpoints)
        var snap = initialMainSnapshot(cleanliness0, cleanlinessFinal)

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

    private fun stepsByCheckpointIndex(
        refactoringSteps: List<RefactoringStep>,
    ): Map<Int, List<RefactoringStep>> {
        val out = HashMap<Int, MutableList<RefactoringStep>>()
        for (s in refactoringSteps) {
            out.getOrPut(s.toCheckpointIndex) { mutableListOf() }.add(s)
        }
        return out
    }

    private fun initialMainSnapshot(
        cleanliness0: Double?,
        cleanlinessFinal: Double?,
    ): ProcessSnapshot = ProcessSnapshot(
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
        cleanlinessFinal = cleanlinessFinal,
        lagSum = 0.0,
        checkpointsSoFar = 0,
    )

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
            val info = compositeInfoByStep[s.stepIndex]
            if (info != null && info.isFirstStepInComposite) {
                compositesCount += 1
                if (!info.hasTestInWindow) skippedCompositesCount += 1
            }
        }

        val testsAtCpSuccess = testsOk && !testsSkipped
        val landsRefactor = stepsLandingHere.isNotEmpty()
        val greenRefactorCp = landsRefactor && build && testsAtCpSuccess
        var greenSinceLastCommit = snap.greenSinceLastCommit
        var commitGapEvents = snap.commitGapEvents
        when {
            cp.isUserCommit -> greenSinceLastCommit = 0
            greenRefactorCp -> {
                greenSinceLastCommit += 1
                if (greenSinceLastCommit >= weights.minCommitGap) {
                    commitGapEvents += 1
                    greenSinceLastCommit = 0
                }
            }
        }

        val brokenCount = snap.brokenCount + brokenInc
        val brokenMs = snap.brokenMs + brokenMsInc
        val elapsedMs = snap.elapsedMs + durationMs

        val altLengthBonusPoints = snap.altLengthBonusPoints

        val checkpointsSoFar = snap.checkpointsSoFar + 1
        // Time-weighted broken fraction: avoids dilution from
        // many-small-checkpoints manual-edit clusters.
        val brokenFrac = if (elapsedMs > 0L) brokenMs.toDouble() / elapsedMs.toDouble() else 0.0
        val skipFrac = if (compositesCount == 0 || skippedCompositesCount == 0) 0.0
            else (skippedCompositesCount + 1).toDouble() / (compositesCount + 2).toDouble()
        val manualFrac = if (ideRelevantCount == 0 || manualWhenIdeCount == 0) 0.0
            else (manualWhenIdeCount + 1).toDouble() / (ideRelevantCount + 2).toDouble()

        val cleanlinessGain = if (cleanT == null || snap.cleanliness0 == null) 0.0
            else cleanT - snap.cleanliness0

        val lagInc = if (cleanT == null || snap.cleanlinessFinal == null) 0.0
            else max(0.0, snap.cleanlinessFinal - cleanT)
        val lagSum = snap.lagSum + lagInc
        val lagFrac = if (checkpointsSoFar == 0) 0.0 else lagSum / checkpointsSoFar.toDouble()

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
            lagFrac = lagFrac,
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
            cleanlinessFinal = snap.cleanlinessFinal,
            lagSum = lagSum,
            checkpointsSoFar = checkpointsSoFar,
        )
        return next to score
    }

    private data class AltProcessOutput(
        val perStepScores: List<List<ProcessScore>>,
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
            val anchor = mainSnapshots[alt.fromSha]
            if (anchor == null) {
                perStepScores += alt.altCheckpoints.map { ProcessScore.EMPTY }
                finalSnapshots += null
                continue
            }

            val altDurations = altStepDurations(alt, userIdxBySha, stepByIndex, userDurations)
            val perStepLengthBonus = computeAltLengthBonusPerStep(alt, userIdxBySha)
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

    private data class AltStepDelta(
        val refactoringStepsInc: Int,
        val ideRelevantInc: Int,
        val compositesInc: Int,
        val skippedCompositesInc: Int,
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
            val info = compositeInfoByStep[s.stepIndex]
            if (info != null && seenCompositesPerAlt.add(info.compositeId)) {
                comp += 1
                val altTested = alt.kind == DivergenceKind.IDE_REPLAY || info.hasTestInWindow
                if (!altTested) skipComp += 1
            }
        }
        return AltStepDelta(
            refactoringStepsInc = ref,
            ideRelevantInc = ide,
            compositesInc = comp,
            skippedCompositesInc = skipComp,
            lengthBonusPoints = 0.0,
            landsRefactor = true,
        )
    }

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
        val totalBonus = weights.length * (userSteps - altSteps).toDouble() / userSteps.toDouble()
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
                val userCpIdx = alt.altCheckpointUserIndexes.getOrNull(k) ?: return@List 0L
                val srcIdx = (userCpIdx - 1).coerceAtLeast(0)
                userDurations.getOrNull(srcIdx) ?: 0L
            }
            else -> List(n) { k ->
                val userStepIdx = alt.stepIndexes.getOrNull(k) ?: return@List 0L
                val userCpIdx = stepByIndex[userStepIdx]?.toCheckpointIndex
                    ?: return@List 0L
                val srcIdx = (userCpIdx - 1).coerceAtLeast(0)
                userDurations.getOrNull(srcIdx) ?: 0L
            }
        }
    }

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

    data class AltProcessContinuation(
        val checkpointShas: List<String>,
        val processScores: List<ProcessScore>,
    ) {
        companion object {
            val EMPTY = AltProcessContinuation(emptyList(), emptyList())
        }
    }

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
        val testsSkippedCount = snap.testsSkippedCount
        val altLengthBonusPoints = snap.altLengthBonusPoints + delta.lengthBonusPoints

        val testsAtAltSuccess = testsOk && !testsSkipped
        val greenRefactorCp = delta.landsRefactor && build && testsAtAltSuccess
        var greenSinceLastCommit = snap.greenSinceLastCommit
        var commitGapEvents = snap.commitGapEvents
        when {
            cp.isUserCommit -> greenSinceLastCommit = 0
            greenRefactorCp -> {
                greenSinceLastCommit += 1
                if (greenSinceLastCommit >= weights.minCommitGap) {
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

        val lagInc = if (cleanT == null || snap.cleanlinessFinal == null) 0.0
            else max(0.0, snap.cleanlinessFinal - cleanT)
        val lagSum = snap.lagSum + lagInc
        val lagFrac = if (checkpointsSoFar == 0) 0.0 else lagSum / checkpointsSoFar.toDouble()

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
            lagFrac = lagFrac,
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
            cleanlinessFinal = snap.cleanlinessFinal,
            lagSum = lagSum,
            checkpointsSoFar = checkpointsSoFar,
        )
        return next to score
    }

    private fun stepUserRanTests(checkpoints: List<CheckpointReport>, s: RefactoringStep): Boolean {
        val cp = checkpoints.getOrNull(s.toCheckpointIndex) ?: return false
        return cp.events.any { it.type == EventType.TEST_RUN_FINISHED }
    }

    private data class CompositeInfo(
        val compositeId: Int,
        val isFirstStepInComposite: Boolean,
        val hasTestInWindow: Boolean,
    )

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
            val isFirst = previous == null || (s.timestamp - previous) > weights.compositeGapMs
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
        lagFrac: Double,
    ): ProcessScore {
        val cleanlinessPoints = weights.gain * cleanlinessGain
        val brokenPoints = -weights.broken * brokenFrac
        val skipPoints = -weights.skipTests * skipFrac
        val manualPoints = -weights.manualIde * manualFrac
        val commitGapPoints = -weights.commitGap * commitGapEvents.toDouble()
        val lagPoints = -weights.lag * lagFrac

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
                    "≥${weights.minCommitGap} green refactor checkpoints without a commit",
            ),
            ProcessContribution(
                id = "altLength",
                label = "Step-savings bonus",
                points = altLengthBonusPoints,
                detail = if (altLengthBonusPoints <= 0.0) "no step savings"
                else "alt covers the user's window in fewer steps " +
                    "(bonus locked in at merge)",
            ),
            ProcessContribution(
                id = "lag",
                label = "Intermediate cleanliness lag",
                points = lagPoints,
                detail = if (lagFrac <= 0.0) "no lag below final cleanliness"
                else "mean deficit ${pct(lagFrac)} of cleanliness range " +
                    "below the trajectory's final value",
            ),
        )

        val unclamped = weights.baseline +
            cleanlinessPoints + brokenPoints +
            skipPoints + manualPoints + commitGapPoints + altLengthBonusPoints +
            lagPoints
        val clampedValue = max(0.0, min(100.0, unclamped))
        val total = clampedValue.roundToLong().toInt()
        val clamped = unclamped != clampedValue
        return ProcessScore(
            total = total,
            baseline = weights.baseline,
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

}
