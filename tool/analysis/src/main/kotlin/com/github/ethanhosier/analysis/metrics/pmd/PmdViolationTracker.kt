package com.github.ethanhosier.analysis.metrics.pmd

import com.github.ethanhosier.analysis.metrics.model.PmdTracking
import com.github.ethanhosier.analysis.metrics.pmd.DiffLineMapper.Mapped
import kotlin.math.abs

/**
 * Stateless layer-3c tracker: given the previous and current checkpoints'
 * raw PMD output, plus a way to translate prev-side paths and prev-side
 * lines onto curr-side coordinates, decides which curr violations are
 * carried over (and therefore inherit a `firstSeenAtSha`) and which prev
 * violations no longer fire (and therefore land in `resolvedSincePrev`).
 *
 * Matching is `(file, rule)`-bucketed; within a bucket we prefer an exact
 * translated-line match, falling back to `±lineTolerance` to absorb the
 * ±1 jitter PMD sometimes shows when an edit nudges the AST node a rule
 * binds to. Multiset bookkeeping (each curr violation can only be claimed
 * once) means N adjacent flags of the same rule resolve cleanly when one
 * is deleted.
 *
 * The tracker has no opinion on how `translatePath` and `mapperFor` were
 * built — they're caller-supplied so the wiring stage can derive them
 * from `git diff -M --name-status` and `git diff -U0`. That keeps this
 * file pure and unit-testable without any git dependency.
 */
object PmdViolationTracker {

    /** Default ±line window applied after exact-line matching fails. */
    const val DEFAULT_LINE_TOLERANCE = 1

    /**
     * Tracking for a checkpoint with no predecessor: every violation is
     * stamped as first-seen at this checkpoint, nothing resolved.
     */
    fun seed(currSha: String, curr: PmdResult): PmdTracking = PmdTracking(
        firstSeenAtSha = List(curr.violations.size) { currSha },
        resolvedSincePrev = emptyList(),
    )

    /**
     * @param prevSha SHA of the previous checkpoint
     * @param currSha SHA of this checkpoint
     * @param prev PMD output at `prevSha`
     * @param curr PMD output at `currSha`
     * @param prevTracking the tracking already computed for `prev`, used
     *   to chain `firstSeenAtSha` further back than just `prevSha` for
     *   violations that have been carried for several checkpoints
     * @param translatePath maps a prev-side relative path to its curr-side
     *   counterpart, or `null` if the file has been deleted
     * @param mapperFor returns the line mapper for a curr-side path; used
     *   only after `translatePath` has succeeded
     * @param lineTolerance ± window for fallback line matching
     */
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

        for (prevIdx in prev.violations.indices) {
            val pv = prev.violations[prevIdx]
            // Carried-from-further-back chain: if the prev tracking has a
            // stamp, that's already the earliest sighting; otherwise this
            // is the first hop and the prev sha itself is the origin.
            val originSha = prevTracking.firstSeenAtSha.getOrNull(prevIdx) ?: prevSha

            val newPath = translatePath(pv.file)
            if (newPath == null) {
                resolved += pv.toResolved(originSha)
                continue
            }
            val translatedLine = when (val mapped = mapperFor(newPath).map(pv.beginLine)) {
                Mapped.Deleted -> {
                    resolved += pv.toResolved(originSha)
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
                resolved += pv.toResolved(originSha)
            }
        }

        val firstSeen = List(curr.violations.size) { i ->
            carriedFirstSeen[i] ?: currSha
        }
        return PmdTracking(firstSeenAtSha = firstSeen, resolvedSincePrev = resolved)
    }

    private fun pickMatch(
        candidates: List<Int>,
        claimed: BooleanArray,
        curr: PmdResult,
        translatedLine: Int,
        lineTolerance: Int,
    ): Int? {
        // Exact match has priority — guarantees that adjacent same-rule
        // siblings each pair up with their own counterpart instead of
        // greedily stealing a neighbour's via the tolerance window.
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
