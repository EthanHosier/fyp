package com.github.ethanhosier.analysis.metrics.cpd

import com.github.ethanhosier.analysis.pipeline.CpdTracking
import com.github.ethanhosier.analysis.pipeline.ResolvedCpdDuplication

object CpdDuplicationTracker {

    fun seed(currSha: String, curr: CpdResult): CpdTracking = CpdTracking(
        firstSeenAtSha = List(curr.duplications.size) { currSha },
        resolvedSincePrev = emptyList(),
    )

    fun track(
        currSha: String,
        prev: CpdResult,
        curr: CpdResult,
        prevTracking: CpdTracking,
    ): CpdTracking {
        val prevByIdentity = HashMap<String, Pair<Int, String>>()
        for (i in prev.duplications.indices) {
            val id = prev.duplications[i].identity
            if (id.isBlank()) continue
            val firstSeen = prevTracking.firstSeenAtSha.getOrNull(i) ?: currSha
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
