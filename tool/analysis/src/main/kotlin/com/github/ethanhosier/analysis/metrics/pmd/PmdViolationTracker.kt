package com.github.ethanhosier.analysis.metrics.pmd

import com.github.ethanhosier.analysis.pipeline.PmdTracking
import com.github.ethanhosier.analysis.metrics.pmd.DiffLineMapper.Mapped
import kotlin.math.abs

object PmdViolationTracker {

    const val DEFAULT_LINE_TOLERANCE = 1

    fun seed(currSha: String, curr: PmdResult): PmdTracking = PmdTracking(
        firstSeenAtSha = List(curr.violations.size) { currSha },
        resolvedSincePrev = emptyList(),
    )

    fun track(
        prevSha: String,
        currSha: String,
        prev: PmdResult,
        curr: PmdResult,
        prevTracking: PmdTracking,
        translatePath: (prevPath: String) -> String?,
        mapperFor: (currPath: String) -> DiffLineMapper,
        lineTolerance: Int = DEFAULT_LINE_TOLERANCE,
    ): PmdTracking {
        // Build a (file, rule) → unclaimed curr indices index for fast lookup.
        val currIndex = HashMap<Pair<String, String>, MutableList<Int>>()
        for (i in curr.violations.indices) {
            val v = curr.violations[i]
            currIndex.getOrPut(v.file to v.rule) { mutableListOf() }.add(i)
        }

        val claimed = BooleanArray(curr.violations.size)
        val carriedFirstSeen = arrayOfNulls<String>(curr.violations.size)
        val resolved = ArrayList<ResolvedPmdViolation>()

        data class Deferred(val pv: PmdViolation, val originSha: String)
        val deferred = ArrayList<Deferred>()

        for (prevIdx in prev.violations.indices) {
            val pv = prev.violations[prevIdx]
            val originSha = prevTracking.firstSeenAtSha.getOrNull(prevIdx) ?: prevSha

            val newPath = translatePath(pv.file)
            if (newPath == null) {
                deferred += Deferred(pv, originSha)
                continue
            }
            val translatedLine = when (val mapped = mapperFor(newPath).map(pv.beginLine)) {
                Mapped.Deleted -> {
                    deferred += Deferred(pv, originSha)
                    continue
                }
                is Mapped.Translated -> mapped.line
            }

            val candidates = currIndex[newPath to pv.rule] ?: emptyList()
            val matchedCurrIdx = pickMatch(candidates, claimed, curr, translatedLine, lineTolerance)
            if (matchedCurrIdx != null) {
                claimed[matchedCurrIdx] = true
                carriedFirstSeen[matchedCurrIdx] = originSha
            } else {
                deferred += Deferred(pv, originSha)
            }
        }

        val currByFingerprint = HashMap<Pair<String, String>, ArrayDeque<Int>>()
        for (i in curr.violations.indices) {
            if (claimed[i]) continue
            val fp = snippetFingerprint(curr.violations[i]) ?: continue
            currByFingerprint
                .getOrPut(curr.violations[i].rule to fp) { ArrayDeque() }
                .addLast(i)
        }
        for (d in deferred) {
            val fp = snippetFingerprint(d.pv)
            val bucket = if (fp != null) currByFingerprint[d.pv.rule to fp] else null
            val matched = bucket?.removeFirstOrNull()
            if (matched != null && !claimed[matched]) {
                claimed[matched] = true
                carriedFirstSeen[matched] = d.originSha
            } else {
                resolved += d.pv.toResolved(d.originSha)
            }
        }

        val firstSeen = List(curr.violations.size) { i ->
            carriedFirstSeen[i] ?: currSha
        }
        return PmdTracking(firstSeenAtSha = firstSeen, resolvedSincePrev = resolved)
    }

    private fun snippetFingerprint(v: PmdViolation): String? {
        val patch = v.snippet?.patch ?: return null
        val lines = patch.lineSequence().toList()
        val hunkIdx = lines.indexOfFirst { it.startsWith("@@") }
        if (hunkIdx < 0) return null
        val header = HUNK_HEADER.find(lines[hunkIdx]) ?: return null
        val startLine = header.groupValues[1].toIntOrNull() ?: return null
        val body = lines.subList(hunkIdx + 1, lines.size)
            .takeWhile { it.startsWith(" ") || it.isEmpty() }
        val from = v.beginLine - startLine
        val to = v.endLine - startLine + 1
        if (from < 0 || to > body.size || from >= to) return null
        val core = body.subList(from, to)
            .joinToString("\n") { it.removePrefix(" ").replace(WS, " ").trim() }
        return core.ifBlank { null }
    }

    private val HUNK_HEADER = Regex("@@ -(\\d+)(?:,\\d+)?")
    private val WS = Regex("\\s+")

    private fun pickMatch(
        candidates: List<Int>,
        claimed: BooleanArray,
        curr: PmdResult,
        translatedLine: Int,
        lineTolerance: Int,
    ): Int? {
        for (ci in candidates) {
            if (!claimed[ci] && curr.violations[ci].beginLine == translatedLine) return ci
        }
        if (lineTolerance <= 0) return null
        var bestIdx: Int? = null
        var bestDist = Int.MAX_VALUE
        for (ci in candidates) {
            if (claimed[ci]) continue
            val dist = abs(curr.violations[ci].beginLine - translatedLine)
            if (dist <= lineTolerance && dist < bestDist) {
                bestIdx = ci
                bestDist = dist
            }
        }
        return bestIdx
    }

    private fun PmdViolation.toResolved(firstSeenAtSha: String): ResolvedPmdViolation =
        ResolvedPmdViolation(
            rule = rule,
            ruleSet = ruleSet,
            priority = priority,
            prevFile = file,
            prevBeginLine = beginLine,
            prevEndLine = endLine,
            message = message,
            snippet = snippet,
            firstSeenAtSha = firstSeenAtSha,
        )
}
