package com.github.ethanhosier.analysis.metrics.pmd

import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.pipeline.PmdTracking
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import kotlinx.serialization.Serializable

/**
 * Sequential post-processing pass that computes [PmdTracking] for every
 * checkpoint by walking the trajectory in order and feeding consecutive
 * `(prev, curr)` pairs to [PmdViolationTracker].
 *
 * For each pair we ask git for two things:
 *  - `--name-status -M` to learn which files were renamed/deleted between
 *    the two SHAs, so prev-side paths translate onto curr-side paths;
 *  - `diff -U0` for the unified-diff text, which the tracker turns into a
 *    per-file [DiffLineMapper] to translate violation line numbers.
 *
 * Cheap relative to the PMD pass itself — one diff invocation per
 * transition, regardless of how many violations the file carries.
 *
 * Alternative trajectories are tracked independently: each `(fromSha,
 * altSha)` pair is a single hop relative to the user's `fromSha`, so the
 * tracker is fed the user's tracking at `fromSha` as `prevTracking` and
 * a fresh diff between those two SHAs.
 */
class PmdTrackingRunner(private val git: GitRunner) {

    @Serializable
    data class Summary(
        /** Keyed by user-trajectory SHA, in trajectory order. */
        val trackingBySha: Map<String, PmdTracking>,
        /** Keyed by alternative SHA. */
        val alternativeTrackingBySha: Map<String, PmdTracking>,
    )

    /**
     * @param orderedUserCheckpoints user's checkpoints in trajectory order
     * @param alternativePairs `(fromSha, altSha)` for each synthesised
     *   alternative; `fromSha` must appear in [orderedUserCheckpoints]
     * @param alternativeCheckpoints metrics for each alternative SHA;
     *   matched to [alternativePairs] by `sha`
     */
    fun run(
        orderedUserCheckpoints: List<CheckpointMetrics>,
        alternativePairs: List<Pair<String, String>> = emptyList(),
        alternativeCheckpoints: List<CheckpointMetrics> = emptyList(),
    ): Summary {
        val userTracking = LinkedHashMap<String, PmdTracking>()
        for ((i, curr) in orderedUserCheckpoints.withIndex()) {
            userTracking[curr.sha] = if (i == 0) {
                PmdViolationTracker.seed(curr.sha, curr.pmd)
            } else {
                val prev = orderedUserCheckpoints[i - 1]
                trackPair(prev, curr, userTracking[prev.sha] ?: PmdTracking.EMPTY)
            }
        }

        val altMetricsBySha = alternativeCheckpoints.associateBy { it.sha }
        val userMetricsBySha = orderedUserCheckpoints.associateBy { it.sha }
        // Multi-step alts chain: pair k+1's `fromSha` is pair k's
        // `altSha`. Look `from` up in both buckets and inherit the
        // running tracking from whichever side it lives on.
        val combinedMetricsBySha = userMetricsBySha + altMetricsBySha
        val combinedTrackingBySha = LinkedHashMap<String, PmdTracking>().apply {
            putAll(userTracking)
        }
        val altTracking = LinkedHashMap<String, PmdTracking>()
        for ((fromSha, altSha) in alternativePairs) {
            val from = combinedMetricsBySha[fromSha] ?: continue
            val alt = altMetricsBySha[altSha] ?: continue
            val prevTracking = combinedTrackingBySha[fromSha] ?: PmdTracking.EMPTY
            val tracking = trackPair(from, alt, prevTracking)
            altTracking[altSha] = tracking
            combinedTrackingBySha[altSha] = tracking
        }

        return Summary(userTracking, altTracking)
    }

    private fun trackPair(
        prev: CheckpointMetrics,
        curr: CheckpointMetrics,
        prevTracking: PmdTracking,
    ): PmdTracking {
        val pathTranslate = buildPathTranslator(prev.sha, curr.sha)
        // -U0 keeps hunks tight: independent edits inside the same file
        // become separate hunks, which lets `DiffLineMapper`'s equal-count
        // heuristic apply per-edit instead of getting buried under
        // unrelated context lines.
        val patch = git.diffPatch(prev.sha, curr.sha, contextLines = 0)
        // Cache mappers per requested path so a file with N violations
        // doesn't re-parse the same section N times.
        val mapperCache = HashMap<String, DiffLineMapper>()
        val mapperFor: (String) -> DiffLineMapper = { path ->
            mapperCache.getOrPut(path) { DiffLineMapper.forFile(patch, path) }
        }
        return PmdViolationTracker.track(
            prevSha = prev.sha,
            currSha = curr.sha,
            prev = prev.pmd,
            curr = curr.pmd,
            prevTracking = prevTracking,
            translatePath = pathTranslate,
            mapperFor = mapperFor,
        )
    }

    private fun buildPathTranslator(from: String, to: String): (String) -> String? {
        val deleted = HashSet<String>()
        val renamed = HashMap<String, String>()
        for (line in git.diffNameStatus(from, to)) {
            val parts = line.split('\t')
            if (parts.isEmpty()) continue
            val status = parts[0].firstOrNull() ?: continue
            when (status) {
                'D' -> if (parts.size >= 2) deleted += parts[1]
                'R', 'C' -> if (parts.size >= 3) renamed[parts[1]] = parts[2]
                // 'A' (added) / 'M' (modified) — prev-path equals curr-path
                // (or has no prev-side equivalent and won't appear in any
                // prev violation), so identity is correct.
                else -> Unit
            }
        }
        return { p ->
            when {
                p in deleted -> null
                p in renamed -> renamed[p]
                else -> p
            }
        }
    }
}
