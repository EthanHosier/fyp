package com.github.ethanhosier.analysis.experiment

import kotlin.math.sqrt

/**
 * Ranking-comparison helpers shared by the experiment drivers
 * (`SensitivityExperiment`, and later `DivergenceExperiment`). Both
 * functions operate on rankings expressed as ordered lists of integer
 * step indices: position 0 = "most important" item.
 *
 * Pseudo-rank convention for [kendallTauB]: the two input rankings can
 * disagree on which step indices appear at all (perturbing a weight can
 * drop a divergence point below the floor, or surface a new one). To
 * still compute a single τ over the *union* of indices, any item
 * missing from a side is assigned that side's `size + 1` — i.e. one
 * worse than its actual worst rank, equal across all missing items. So
 * a step ranked 3rd on the baseline but absent from the perturbed
 * ranking becomes (3, size_b + 1) in the pair table; pairs of two
 * missing-on-the-same-side items contribute a tie on that side.
 *
 * Duplicate step indices within a single ranking are preserved (the
 * builder occasionally emits two divergence points sharing a step) —
 * they enter the union as the same key, so duplicates are deduped on
 * the union axis but each ranking's *own* position list is consulted
 * via `indexOf`, which returns the first occurrence. That matches the
 * "most important" semantics we want for top-N comparisons.
 */
object RankingMetrics {

    /**
     * Kendall's τ-b over the union of items in [a] and [b], using the
     * pseudo-rank convention documented on [RankingMetrics]. τ-b
     * handles ties (which the pseudo-ranks introduce in bulk) by
     * normalising over `sqrt((n0 - n1)(n0 - n2))` rather than the
     * naive `n*(n-1)/2`.
     *
     * Returns 1.0 when the rankings agree on every pair, -1.0 when
     * they disagree on every pair, 0.0 when uncorrelated. Returns 1.0
     * when both rankings are empty (vacuously identical) and 0.0 when
     * the denominator collapses (all-tie on either side — rare but
     * possible when one ranking is empty and the other has length 1).
     */
    fun kendallTauB(a: List<Int>, b: List<Int>): Double {
        val union = (a + b).toSet().toList()
        if (union.size < 2) return if (a == b) 1.0 else 0.0

        val missingRankA = a.size + 1
        val missingRankB = b.size + 1

        // Rank lookups: first-occurrence index + 1 so ranks are
        // 1-indexed and the "missing" pseudo-rank sits above them.
        val rankA = union.map { x ->
            val i = a.indexOf(x)
            if (i < 0) missingRankA else i + 1
        }
        val rankB = union.map { x ->
            val i = b.indexOf(x)
            if (i < 0) missingRankB else i + 1
        }

        var concordant = 0L
        var discordant = 0L
        var tiesA = 0L
        var tiesB = 0L
        val n = union.size
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val da = rankA[i] - rankA[j]
                val db = rankB[i] - rankB[j]
                val prod = da.toLong() * db.toLong()
                when {
                    prod > 0 -> concordant++
                    prod < 0 -> discordant++
                    else -> {
                        if (da == 0) tiesA++
                        if (db == 0) tiesB++
                    }
                }
            }
        }
        val totalPairs = n.toLong() * (n - 1) / 2
        val denom = sqrt((totalPairs - tiesA).toDouble() * (totalPairs - tiesB).toDouble())
        if (denom == 0.0) return 0.0
        return (concordant - discordant) / denom
    }

    /**
     * Fraction of [a]'s top-[n] *distinct* step indices that also
     * appear in [b]'s top-[n] distinct step indices. Both sides are
     * deduped before truncation so the denominator matches the
     * numerator's semantics — a ranking like `[4, 4, 7]` contributes
     * two distinct items (`4`, `7`), not three. Denominator is
     * `min(n, distinct(a))` so the sanity case `a == b` always
     * scores 1.0 regardless of duplicate stepIndices.
     *
     * Returns 1.0 when [a] is empty (nothing to miss).
     */
    fun topNHitRate(a: List<Int>, b: List<Int>, n: Int): Double {
        if (a.isEmpty()) return 1.0
        val distinctA = a.distinct()
        val distinctB = b.distinct()
        val topA = distinctA.take(n).toSet()
        val topB = distinctB.take(n).toSet()
        val denom = minOf(n, distinctA.size).toDouble()
        return topA.intersect(topB).size / denom
    }
}
