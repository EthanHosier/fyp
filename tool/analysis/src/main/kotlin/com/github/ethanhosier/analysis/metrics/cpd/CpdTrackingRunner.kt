package com.github.ethanhosier.analysis.metrics.cpd

import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.pipeline.CpdTracking

/**
 * Sequential post-processing pass that computes [CpdTracking] for every
 * checkpoint by walking the trajectory in order and feeding consecutive
 * `(prev, curr)` pairs to [CpdDuplicationTracker].
 *
 * Cheaper than the PMD equivalent — no git diffs, no path translation,
 * no line mapping. Identity is body-hash, computed once at runner time.
 *
 * Alternative trajectories track independently against their `fromSha`
 * snapshot, mirroring [com.github.ethanhosier.analysis.metrics.pmd.PmdTrackingRunner].
 */
class CpdTrackingRunner {

    data class Summary(
        /** Keyed by user-trajectory SHA, in trajectory order. */
        val trackingBySha: Map<String, CpdTracking>,
        /** Keyed by alternative SHA. */
        val alternativeTrackingBySha: Map<String, CpdTracking>,
    )

    fun run(
        orderedUserCheckpoints: List<CheckpointMetrics>,
        alternativePairs: List<Pair<String, String>> = emptyList(),
        alternativeCheckpoints: List<CheckpointMetrics> = emptyList(),
    ): Summary {
        val userTracking = LinkedHashMap<String, CpdTracking>()
        for ((i, curr) in orderedUserCheckpoints.withIndex()) {
            userTracking[curr.sha] = if (i == 0) {
                CpdDuplicationTracker.seed(curr.sha, curr.cpd)
            } else {
                val prev = orderedUserCheckpoints[i - 1]
                CpdDuplicationTracker.track(
                    currSha = curr.sha,
                    prev = prev.cpd,
                    curr = curr.cpd,
                    prevTracking = userTracking[prev.sha] ?: CpdTracking.EMPTY,
                )
            }
        }

        val altMetricsBySha = alternativeCheckpoints.associateBy { it.sha }
        val userMetricsBySha = orderedUserCheckpoints.associateBy { it.sha }
        // Multi-step alts chain: pair k+1's `fromSha` is pair k's
        // `altSha`. Look `from` up in both buckets and inherit the
        // running tracking from whichever side it lives on.
        val combinedMetricsBySha = userMetricsBySha + altMetricsBySha
        val combinedTrackingBySha = LinkedHashMap<String, CpdTracking>().apply {
            putAll(userTracking)
        }
        val altTracking = LinkedHashMap<String, CpdTracking>()
        for ((fromSha, altSha) in alternativePairs) {
            val from = combinedMetricsBySha[fromSha] ?: continue
            val alt = altMetricsBySha[altSha] ?: continue
            val prevTracking = combinedTrackingBySha[fromSha] ?: CpdTracking.EMPTY
            val tracking = CpdDuplicationTracker.track(
                currSha = altSha,
                prev = from.cpd,
                curr = alt.cpd,
                prevTracking = prevTracking,
            )
            altTracking[altSha] = tracking
            combinedTrackingBySha[altSha] = tracking
        }

        return Summary(userTracking, altTracking)
    }
}
