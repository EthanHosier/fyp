package com.github.ethanhosier.analysis.alternative.synthesise

/**
 * Per-window cache mapping ordered prefixes of spec-applies to the
 * git SHA we materialised after applying that exact prefix in that
 * exact order on the window's `fromSha`.
 *
 * Why ordered, not unordered:
 *   The dependency DAG (`SpecDependencyAnalyzer`) is over-optimistic
 *   about which specs commute, so two orderings the DAG declares
 *   equivalent may in fact produce different trees (or one may
 *   JDT-fail where the other doesn't). An ordered-prefix key never
 *   conflates orderings that did differ in execution order — same
 *   prefix means same applies in same order means same tree, period.
 *
 * Lifetime: one instance per reorder window in
 * [ReorderSynthesiser]. Reset between windows because [fromSha]
 * differs and the spec indices refer to a different window's local
 * spec list.
 */
internal class PrefixCache(private val fromSha: String) {

    private val cache: MutableMap<List<Int>, String> =
        mutableMapOf(emptyList<Int>() to fromSha)

    /**
     * Walk [ordering] left-to-right and return the longest prefix
     * for which we have a cached SHA, paired with that SHA. The
     * empty prefix always hits (it maps to `fromSha`), so the
     * return is never null.
     *
     * The remaining suffix `ordering.drop(result.first.size)` is
     * what the caller must replay against a worktree borrowed at
     * `result.second`.
     */
    fun deepestHit(ordering: List<Int>): Pair<List<Int>, String> {
        var bestPrefix: List<Int> = emptyList()
        var bestSha: String = fromSha
        for (i in 1..ordering.size) {
            val candidate = ordering.subList(0, i)
            val sha = cache[candidate] ?: break
            bestPrefix = candidate.toList()
            bestSha = sha
        }
        return bestPrefix to bestSha
    }

    /** Cache the SHA we just materialised for the given prefix. */
    fun put(prefix: List<Int>, sha: String) {
        cache[prefix.toList()] = sha
    }

    /** Look up the SHA for an exact prefix; null if not cached. */
    fun shaFor(prefix: List<Int>): String? = cache[prefix]
}
