package com.github.ethanhosier.analysis.metrics.cpd

import com.github.ethanhosier.analysis.pipeline.CpdTracking
import com.github.ethanhosier.analysis.pipeline.ResolvedCpdDuplication

/**
 * Stateless per-pair tracker for CPD clone groups: given the previous and
 * current checkpoints' [CpdResult]s, decides which curr groups carry over
 * (and inherit a `firstSeenAtSha`) and which prev groups no longer appear
 * (and land in `resolvedSincePrev`).
 *
 * Identity is the body-text hash already computed on each
 * `CpdDuplication.identity` — see [com.github.ethanhosier.analysis.metrics.util.SnippetIdentity].
 * Pure code-motion (file moves, line shifts) preserves identity; edits to
 * the cloned body break it. This is intentionally simpler than the PMD
 * tracker: no path translation, no diff-line mapper, since identity is
 * location-independent.
 *
 * Groups whose `identity` is empty (snippet missing at runner time) are
 * skipped from matching — they carry their own `currSha` stamp but never
 * resolve any prev group. Acceptable: clones without snippets can't be
 * tracked across commits anyway.
 */
object CpdDuplicationTracker {

    /**
     * Tracking for a checkpoint with no predecessor: every group is
     * first-seen here, nothing resolved.
     */
    fun seed(currSha: String, curr: CpdResult): CpdTracking = CpdTracking(
        firstSeenAtSha = List(curr.duplications.size) { currSha },
        resolvedSincePrev = emptyList(),
    )

    /**
     * @param currSha SHA of this checkpoint
     * @param prev CPD output at the previous checkpoint
     * @param curr CPD output at `currSha`
     * @param prevTracking the tracking already computed for `prev`, used
     *   to chain `firstSeenAtSha` further back than just one hop
     */
    fun track(
        currSha: String,
        prev: CpdResult,
        curr: CpdResult,
        prevTracking: CpdTracking,
    ): CpdTracking {
        // Map prev identity → (index, firstSeenAtSha). Skips empty
        // identities (no snippet to fingerprint) so they neither match nor
        // count as resolved.
        val prevByIdentity = HashMap<String, Pair<Int, String>>()
        for (i in prev.duplications.indices) {
            val id = prev.duplications[i].identity
            if (id.isBlank()) continue
            val firstSeen = prevTracking.firstSeenAtSha.getOrNull(i) ?: currSha
            // First write wins — duplicate identities within one CPD report
            // shouldn't happen (groups are unique per checkpoint) but defend
            // anyway so we don't lose the earlier sighting.
            prevByIdentity.putIfAbsent(id, i to firstSeen)
        }

        val matchedPrevIndices = HashSet<Int>()
        val firstSeenAtSha = ArrayList<String>(curr.duplications.size)
        for (dup in curr.duplications) {
            val match = if (dup.identity.isBlank()) null else prevByIdentity[dup.identity]
            if (match != null) {
                matchedPrevIndices += match.first
                firstSeenAtSha += match.second
            } else {
                firstSeenAtSha += currSha
            }
        }

        val resolved = ArrayList<ResolvedCpdDuplication>()
        for (i in prev.duplications.indices) {
            if (i in matchedPrevIndices) continue
            val pdup = prev.duplications[i]
            if (pdup.identity.isBlank()) continue
            resolved += ResolvedCpdDuplication(
                tokens = pdup.tokens,
                lines = pdup.lines,
                identity = pdup.identity,
                prevOccurrences = pdup.occurrences,
                firstSeenAtSha = prevTracking.firstSeenAtSha.getOrNull(i) ?: currSha,
            )
        }

        return CpdTracking(firstSeenAtSha = firstSeenAtSha, resolvedSincePrev = resolved)
    }
}
