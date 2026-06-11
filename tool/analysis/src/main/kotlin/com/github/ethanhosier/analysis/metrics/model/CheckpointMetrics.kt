package com.github.ethanhosier.analysis.metrics.model

import com.github.ethanhosier.analysis.metrics.gradlebuild.BuildResult
import com.github.ethanhosier.analysis.metrics.ck.CkResult
import com.github.ethanhosier.analysis.metrics.cpd.CpdResult
import com.github.ethanhosier.analysis.metrics.pmd.PmdResult
import com.github.ethanhosier.analysis.metrics.readability.ReadabilityResult
import com.github.ethanhosier.analysis.metrics.tests.TestResult
import kotlinx.serialization.Serializable

@Serializable
data class CheckpointMetrics(
    val sha: String,
    val ck: CkResult,
    val pmd: PmdResult,
    val cpd: CpdResult = CpdResult.EMPTY,
    // Same backwards-compat story as `cpd`: default lets pre-readability
    // cached `<sha>.json` deserialise; bust the cache for real values.
    val readability: ReadabilityResult = ReadabilityResult.EMPTY,
    val build: BuildResult,
    val tests: TestResult,
)
