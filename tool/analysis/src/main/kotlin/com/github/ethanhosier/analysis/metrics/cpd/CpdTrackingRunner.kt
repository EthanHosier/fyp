package com.github.ethanhosier.analysis.metrics.cpd

import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.pipeline.CpdTracking
import kotlinx.serialization.Serializable

class CpdTrackingRunner {

    @Serializable
    data class Summary(
        val trackingBySha: Map<String, CpdTracking>,
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
