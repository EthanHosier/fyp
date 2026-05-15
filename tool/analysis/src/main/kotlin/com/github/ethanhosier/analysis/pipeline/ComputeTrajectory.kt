package com.github.ethanhosier.analysis.pipeline

import com.github.ethanhosier.ideplugin.model.TouchedMember

private const val TOP_N = 10
private const val DUPLICATION_SPIKE_THRESHOLD = 20
private const val DUPLICATION_DROP_THRESHOLD = 20
private const val DUPLICATION_FOLLOWUP_WINDOW = 5

/**
 * Reduce a chronological list of [CheckpointReport]s to trajectory-level
 * aggregates. checkpoints[0] is the seed-equivalent starting state (its
 * tree matches the seed — SESSION_STARTED et al don't mutate files), so
 * real transitions are [0]→[1], [1]→[2], ... and `numSteps` is
 * `checkpoints.size - 1`.
 */
internal fun computeTrajectory(checkpoints: List<CheckpointReport>): TrajectoryStats {
    if (checkpoints.isEmpty()) return TrajectoryStats.ZERO

    val transitions = checkpoints.drop(1)
    val numSteps = transitions.size

    val churns = transitions.map { it.diff.totalChurn }
    val totalChurn = churns.sum()
    val perFileTouchCount = LinkedHashMap<String, Int>()
    val perFileChurn = LinkedHashMap<String, Int>()
    for (c in transitions) {
        for (pf in c.diff.perFileChurn) {
            perFileTouchCount.merge(pf.path, 1) { a, b -> a + b }
            perFileChurn.merge(pf.path, pf.linesAdded + pf.linesDeleted) { a, b -> a + b }
        }
    }
    val topNChurn = perFileChurn.values.sortedDescending().take(TOP_N).sum()
    val churnTopNShare = if (totalChurn == 0) 0.0 else topNChurn.toDouble() / totalChurn

    val allEventTs = checkpoints.flatMap { it.events.map { e -> e.timestamp } }
    val totalElapsedMs = if (allEventTs.isEmpty()) 0L else allEventTs.max() - allEventTs.min()

    val totalBrokenMs = computeBrokenTime(checkpoints)

    val classStats = computeMemberTouchStats(transitions) { m ->
        m.className?.takeIf { it.isNotBlank() }
    }
    val methodStats = computeMemberTouchStats(transitions) { m ->
        val cls = m.className?.takeIf { it.isNotBlank() } ?: return@computeMemberTouchStats null
        val sig = m.methodSignature?.takeIf { it.isNotBlank() } ?: return@computeMemberTouchStats null
        "$cls#$sig"
    }

    return TrajectoryStats(
        numSteps = numSteps,
        totalChurn = totalChurn,
        avgChurnPerStep = if (numSteps == 0) 0.0 else totalChurn.toDouble() / numSteps,
        maxChurnOnStep = churns.maxOrNull() ?: 0,
        totalFilesTouched = perFileTouchCount.size,
        perFileTouchCount = perFileTouchCount,
        retouchCount = perFileTouchCount.values.sumOf { if (it > 1) it - 1 else 0 },
        churnTopNShare = churnTopNShare,
        topN = TOP_N,
        totalElapsedMs = totalElapsedMs,
        totalBrokenMs = totalBrokenMs,
        classes = classStats,
        methods = methodStats,
        duplication = computeDuplication(checkpoints),
        readability = computeReadability(checkpoints),
    )
}

/**
 * Surface each checkpoint's [ReadabilitySummary] as a series so a reader can
 * see readability drifting across a trajectory, plus net change from
 * baseline. No composite score — follow-up PR when a validated model lands.
 */
private fun computeReadability(checkpoints: List<CheckpointReport>): ReadabilityTrajectoryStats {
    if (checkpoints.isEmpty()) return ReadabilityTrajectoryStats.ZERO
    val summaries = checkpoints.map { it.metrics.readability.summary }
    val first = summaries.first()
    val last = summaries.last()

    return ReadabilityTrajectoryStats(
        avgLineLengthPerStep = summaries.map { it.avgLineLength },
        maxLineLengthPerStep = summaries.map { it.maxLineLength },
        avgCommentRatioPerStep = summaries.map { it.avgCommentRatio },
        avgIndentationPerStep = summaries.map { it.avgIndentation },
        avgIdentifierLengthPerStep = summaries.map { it.avgIdentifierLength },
        singleLetterRatioPerStep = summaries.map { it.singleLetterRatio },
        avgWordCountPerStep = summaries.map { it.avgWordCount },
        dictionaryWordRatioPerStep = summaries.map { it.dictionaryWordRatio },
        worstClassLocPerStep = summaries.map { it.worstClassLoc },
        worstMethodLocPerStep = summaries.map { it.worstMethodLoc },
        netAvgLineLengthChange = last.avgLineLength - first.avgLineLength,
        netAvgCommentRatioChange = last.avgCommentRatio - first.avgCommentRatio,
        netAvgIdentifierLengthChange = last.avgIdentifierLength - first.avgIdentifierLength,
        netSingleLetterRatioChange = last.singleLetterRatio - first.singleLetterRatio,
        netDictionaryWordRatioChange = last.dictionaryWordRatio - first.dictionaryWordRatio,
        netWorstMethodLocChange = last.worstMethodLoc - first.worstMethodLoc,
        netWorstClassLocChange = last.worstClassLoc - first.worstClassLoc,
    )
}

/**
 * Aggregate per-checkpoint CPD output into trajectory-level duplication
 * stats. `perStep` lists are aligned with [checkpoints] (include the seed
 * baseline); deltas and totals cover transitions only.
 *
 * Spike-then-drop: single forward pass. On a positive line-delta >=
 * [DUPLICATION_SPIKE_THRESHOLD], look forward up to [DUPLICATION_FOLLOWUP_WINDOW]
 * transitions; if cumulative drop since the spike is >= [DUPLICATION_DROP_THRESHOLD]
 * and duplication is back at or below the pre-spike level, count once and
 * skip past the matched drop so overlapping spikes don't double-count.
 */
private fun computeDuplication(checkpoints: List<CheckpointReport>): DuplicationTrajectoryStats {
    if (checkpoints.isEmpty()) return DuplicationTrajectoryStats.ZERO

    val linesPerStep = checkpoints.map { it.metrics.cpd.duplicatedLines }
    val blocksPerStep = checkpoints.map { it.metrics.cpd.duplicationBlocks }

    val linesDelta = IntArray(checkpoints.size - 1) { i -> linesPerStep[i + 1] - linesPerStep[i] }
    val blocksDelta = IntArray(checkpoints.size - 1) { i -> blocksPerStep[i + 1] - blocksPerStep[i] }

    val totalLinesIntroduced = linesDelta.filter { it > 0 }.sum()
    val totalLinesRemoved = linesDelta.filter { it < 0 }.sumOf { -it }
    val totalBlocksIntroduced = blocksDelta.filter { it > 0 }.sum()
    val totalBlocksRemoved = blocksDelta.filter { it < 0 }.sumOf { -it }

    val baseline = linesPerStep.first()
    val stepsAboveBaseline = linesPerStep.drop(1).count { it > baseline }

    var spikeThenDrop = 0
    var i = 0
    while (i < linesDelta.size) {
        val delta = linesDelta[i]
        if (delta >= DUPLICATION_SPIKE_THRESHOLD) {
            val preSpikeLevel = linesPerStep[i]
            val end = minOf(linesDelta.size, i + 1 + DUPLICATION_FOLLOWUP_WINDOW)
            var matched = -1
            for (j in i + 1 until end) {
                val levelAfter = linesPerStep[j + 1]
                val dropFromPeak = linesPerStep[i + 1] - levelAfter
                if (dropFromPeak >= DUPLICATION_DROP_THRESHOLD && levelAfter <= preSpikeLevel) {
                    matched = j
                    break
                }
            }
            if (matched >= 0) {
                spikeThenDrop++
                i = matched + 1
                continue
            }
        }
        i++
    }

    return DuplicationTrajectoryStats(
        duplicatedLinesPerStep = linesPerStep,
        duplicatedBlocksPerStep = blocksPerStep,
        linesDeltaPerStep = linesDelta.toList(),
        blocksDeltaPerStep = blocksDelta.toList(),
        totalLinesIntroduced = totalLinesIntroduced,
        totalLinesRemoved = totalLinesRemoved,
        netLinesChange = linesPerStep.last() - linesPerStep.first(),
        totalBlocksIntroduced = totalBlocksIntroduced,
        totalBlocksRemoved = totalBlocksRemoved,
        maxDuplicatedLines = linesPerStep.max(),
        maxSpikeLines = linesDelta.maxOrNull() ?: 0,
        maxDropLines = linesDelta.minOrNull()?.let { if (it < 0) -it else 0 } ?: 0,
        stepsIncreasing = linesDelta.count { it > 0 },
        stepsAboveBaseline = stepsAboveBaseline,
        spikeThenDropCount = spikeThenDrop,
        spikeThreshold = DUPLICATION_SPIKE_THRESHOLD,
        dropThreshold = DUPLICATION_DROP_THRESHOLD,
        followupWindow = DUPLICATION_FOLLOWUP_WINDOW,
    )
}

/**
 * Reduce a list of step-indexed [CheckpointReport]s to per-member touch
 * concentration stats. [keyFor] projects each [TouchedMember] to its
 * identifying key (or null to skip). Callers pass the list *after*
 * dropping the seed-equivalent first checkpoint.
 */
private fun computeMemberTouchStats(
    transitions: List<CheckpointReport>,
    keyFor: (TouchedMember) -> String?,
): MemberTouchStats {
    if (transitions.isEmpty()) return MemberTouchStats.ZERO

    val perStepIndices = LinkedHashMap<String, MutableList<Int>>()
    for ((i, step) in transitions.withIndex()) {
        val keysThisStep = LinkedHashSet<String>()
        for (member in step.touchedMembers) {
            val k = keyFor(member) ?: continue
            keysThisStep.add(k)
        }
        for (k in keysThisStep) {
            perStepIndices.getOrPut(k) { mutableListOf() }.add(i)
        }
    }

    val perTouchCount = LinkedHashMap<String, Int>()
    for ((k, idxs) in perStepIndices) perTouchCount[k] = idxs.size

    val retouchCount = perTouchCount.values.sumOf { if (it > 1) it - 1 else 0 }
    val consecutiveRetouchCount = perStepIndices.values.sumOf { idxs ->
        var n = 0
        for (j in 1 until idxs.size) if (idxs[j] == idxs[j - 1] + 1) n++
        n
    }

    val sorted = perTouchCount.entries.sortedByDescending { it.value }
    val topTouched = sorted.take(TOP_N).map { it.key }
    val totalTouches = perTouchCount.values.sum()
    val topNTouches = sorted.take(TOP_N).sumOf { it.value }
    val topNShare = if (totalTouches == 0) 0.0 else topNTouches.toDouble() / totalTouches

    return MemberTouchStats(
        perTouchCount = perTouchCount,
        retouchCount = retouchCount,
        consecutiveRetouchCount = consecutiveRetouchCount,
        distinctTouched = perTouchCount.size,
        topTouched = topTouched,
        topNShare = topNShare,
        topN = TOP_N,
        perStepIndices = perStepIndices.mapValues { it.value.toList() },
    )
}

/**
 * Walk checkpoints in order. A checkpoint is healthy iff build + tests both
 * pass. When we enter a failing run, remember its first event timestamp;
 * when we hit a healthy checkpoint, add the elapsed ms to the broken total.
 * If the trajectory ends failing, charge the interval up to its last event.
 */
private fun computeBrokenTime(checkpoints: List<CheckpointReport>): Long {
    fun healthy(c: CheckpointReport) = c.metrics.build.success && c.metrics.tests.success
    fun firstTs(c: CheckpointReport) = c.events.minOfOrNull { it.timestamp }
    fun lastTs(c: CheckpointReport) = c.events.maxOfOrNull { it.timestamp }

    var broken = 0L
    var failStart: Long? = null
    for (c in checkpoints) {
        if (!healthy(c)) {
            if (failStart == null) failStart = firstTs(c)
        } else {
            val start = failStart
            val end = firstTs(c)
            if (start != null && end != null) broken += end - start
            failStart = null
        }
    }
    if (failStart != null) {
        val end = checkpoints.lastOrNull()?.let(::lastTs)
        if (end != null) broken += end - failStart
    }
    return broken
}
