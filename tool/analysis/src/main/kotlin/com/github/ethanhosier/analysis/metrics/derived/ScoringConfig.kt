package com.github.ethanhosier.analysis.metrics.derived

import kotlinx.serialization.Serializable

@Serializable
data class ProcessScoreWeights(
    val gain: Double = 50.0,
    val broken: Double = 28.0,
    val skipTests: Double = 14.0,
    val manualIde: Double = 11.0,
    val length: Double = 11.0,
    val commitGap: Double = 7.0,
    val baseline: Int = 50,
    val minCommitGap: Int = 6,
    val compositeGapMs: Long = 60_000L,
)

@Serializable
data class CleanlinessWeights(
    val cognitive: Double = 1.0,
    val coupling: Double = 1.0,
    val duplication: Double = 1.0,
    val readability: Double = 1.0,
    val smells: Double = 1.0,
    val cohesion: Double = 1.0,
)

@Serializable
data class ScoringConfig(
    val process: ProcessScoreWeights = ProcessScoreWeights(),
    val cleanliness: CleanlinessWeights = CleanlinessWeights(),
) {
    companion object {
        val PRODUCTION = ScoringConfig()
    }
}
