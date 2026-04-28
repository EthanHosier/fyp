package com.github.ethanhosier.analysis.metrics.model

import com.github.ethanhosier.analysis.metrics.gradlebuild.BuildResult
import com.github.ethanhosier.analysis.metrics.ck.CkResult
import com.github.ethanhosier.analysis.metrics.cpd.CpdResult
import com.github.ethanhosier.analysis.metrics.pmd.PmdResult
import com.github.ethanhosier.analysis.metrics.readability.ReadabilityResult
import com.github.ethanhosier.analysis.metrics.tests.TestResult
import kotlinx.serialization.Serializable

/**
 * Metrics computed for a single unique commit SHA in the shadow repo.
 *
 * Every section is mandatory. If any runner cannot produce a verdict for any
 * reason (CK crashes on unparseable source, PMD OOMs, `./gradlew` is missing
 * so build/tests can't even start, subprocess times out, …) the run aborts.
 * Downstream code can trust that a written `<sha>.json` is complete.
 */
@Serializable
data class CheckpointMetrics(
    val sha: String,
    val ck: CkResult,
    val pmd: PmdResult,
    // Defaulted so already-cached `<sha>.json` files from before CPD landed
    // still deserialise — they'll just carry an empty CpdResult. Bust the
    // cache (`rm -rf <sessionDir>/checkpoint-metrics/`) to get real CPD data.
    val cpd: CpdResult = CpdResult.EMPTY,
    // Same backwards-compat story as `cpd`: default lets pre-readability
    // cached `<sha>.json` deserialise; bust the cache for real values.
    val readability: ReadabilityResult = ReadabilityResult.EMPTY,
    val build: BuildResult,
    val tests: TestResult,
)
