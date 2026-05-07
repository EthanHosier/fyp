package com.github.ethanhosier.analysis.alternative.reorder

/**
 * Hard limits on enumeration. v1 ships with conservative defaults;
 * beam search beyond [maxNodes] is a follow-up PR.
 */
data class EnumerationBudget(
    /** Skip windows with more than this many nodes. */
    val maxNodes: Int = 7,
    /** Stop emitting after this many orderings; report `truncated=true`. */
    val maxOrderings: Int = 5040,
)

data class EnumerationResult(
    /** Each ordering = a permutation of node indices into [SpecDag.nodes]. */
    val orderings: List<List<Int>>,
    /** True iff a budget was hit (either maxNodes or maxOrderings). */
    val truncated: Boolean,
    /** Populated when nodes > maxNodes; otherwise null. */
    val skipReason: String? = null,
)

object TopologicalEnumerator {

    /**
     * All valid topological orderings of [dag] up to the budget.
     *
     * Returns immediately with `orderings = []`, `truncated = true`,
     * `skipReason != null` when `dag.nodes.size > budget.maxNodes`.
     *
     * Otherwise enumerates exhaustively via classic backtracking
     * (Kahn's algorithm with branching). When emitted count reaches
     * [EnumerationBudget.maxOrderings], stops with `truncated = true`.
     *
     * The user's input order is always emitted (and is the first
     * ordering when no two nodes are mutually independent at depth 0).
     */
    fun enumerate(dag: SpecDag, budget: EnumerationBudget = EnumerationBudget()): EnumerationResult {
        val n = dag.nodes.size
        if (n > budget.maxNodes) {
            return EnumerationResult(
                orderings = emptyList(),
                truncated = true,
                skipReason = "n=$n exceeds budget maxNodes=${budget.maxNodes}",
            )
        }

        val inDegree = IntArray(n)
        for ((src, succs) in dag.edges) {
            for (dst in succs) {
                if (src in 0 until n && dst in 0 until n) inDegree[dst]++
            }
        }

        val out = ArrayList<List<Int>>()
        val current = ArrayDeque<Int>()
        var truncated = false

        fun recurse() {
            if (truncated) return
            if (current.size == n) {
                out.add(current.toList())
                if (out.size >= budget.maxOrderings) truncated = true
                return
            }
            for (i in 0 until n) {
                if (inDegree[i] == 0) {
                    current.addLast(i)
                    inDegree[i] = -1
                    dag.successors(i).forEach { inDegree[it]-- }
                    recurse()
                    dag.successors(i).forEach { inDegree[it]++ }
                    inDegree[i] = 0
                    current.removeLast()
                    if (truncated) return
                }
            }
        }

        recurse()
        return EnumerationResult(orderings = out, truncated = truncated)
    }
}
