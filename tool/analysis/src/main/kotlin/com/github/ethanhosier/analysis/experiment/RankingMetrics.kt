package com.github.ethanhosier.analysis.experiment

import kotlin.math.sqrt

object RankingMetrics {

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
