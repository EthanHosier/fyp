package com.github.ethanhosier.analysis.pipeline

import com.github.ethanhosier.analysis.metrics.model.CheckpointReport
import com.github.ethanhosier.analysis.metrics.model.MemberTouchStats
import com.github.ethanhosier.analysis.metrics.model.TrajectoryStats
import com.github.ethanhosier.ideplugin.model.TouchedMember

private const val TOP_N = 10

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
