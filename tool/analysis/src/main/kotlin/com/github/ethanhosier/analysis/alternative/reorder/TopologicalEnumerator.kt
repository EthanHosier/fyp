package com.github.ethanhosier.analysis.alternative.reorder

data class EnumerationBudget(
    val maxNodes: Int = 7,
    val maxOrderings: Int = 5040,
)

data class EnumerationResult(
    val orderings: List<List<Int>>,
    val truncated: Boolean,
    val skipReason: String? = null,
)

object TopologicalEnumerator {

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
