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

        // Prev violations whose path/line lookup misses go here instead of
        // straight to `resolved`, so the moved-block fallback pass below
        // gets a chance to claim a curr counterpart by snippet fingerprint
        // (Extract Method etc., where the rule fires on the same source
        // text but at a new file/line that path+line tracking can't reach).
        data class Deferred(val pv: PmdViolation, val originSha: String)
        val deferred = ArrayList<Deferred>()

        for (prevIdx in prev.violations.indices) {
            val pv = prev.violations[prevIdx]
            // Carried-from-further-back chain: if the prev tracking has a
            // stamp, that's already the earliest sighting; otherwise this
            // is the first hop and the prev sha itself is the origin.
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

        // Moved-block fallback. Build a (rule, snippet-fingerprint) → curr
        // index over still-unclaimed curr violations, then walk the
        // deferred prevs in order and claim at most one curr per prev. Each
        // curr can only be claimed once (existing `claimed[]` invariant)
        // and each deferred prev consumes its snippet fingerprint at most
        // once, so duplicate occurrences pair up 1:1 instead of one prev
        // claiming all currs with the same body.
        //
        // This is intentionally coarse and not perfect:
        //  - pure text comparison (whitespace-collapsed) — a refactor that
        //    edits the block (renamed local, tweaked literal) won't match;
        //  - same-rule-only — won't bridge a rule rename;
        //  - no AST/structural equivalence — two unrelated blocks that
        //    happen to share text will incorrectly pair;
        //  - relies on snippet availability (null when PMD couldn't read
        //    the source); falls back to the original resolved-as-deleted
        //    behaviour when missing.
        // It's enough to stop the obvious Extract Method double-count
        // (resolved-here + introduced-there for the same logical smell)
        // without claiming to be a full move detector.
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
            // ArrayDeque + removeFirstOrNull guarantees one-shot consumption
            // of each curr fingerprint slot — a second deferred prev with
            // the same fingerprint will fall through to `resolved`.
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

    /**
     * Whitespace-collapsed concatenation of just the `beginLine..endLine`
     * lines of a violation's snippet — i.e. the actual offending block,
     * stripped of the surrounding context padding the snippet carries for
     * dashboard rendering. Used as the moved-block identity key.
     *
     * Null when the snippet isn't available, the embedded `@@` header
     * can't be parsed, or the offending range falls outside the snippet
     * body — caller treats null as "no fingerprint, can't move-match".
     */
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
