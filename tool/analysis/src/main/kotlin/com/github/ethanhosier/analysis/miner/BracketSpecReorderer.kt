package com.github.ethanhosier.analysis.miner

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec

object BracketSpecReorderer {

    fun reorder(specs: List<RefactoringSpec?>): List<Int> {
        if (specs.size < 2) return specs.indices.toList()

        // Index every InlineMethod target in the bracket.
        val inlineTargets = HashMap<Pair<String, String>, Int>()
        for (i in specs.indices) {
            val s = specs[i]
            if (s is RefactoringSpec.InlineMethod) {
                inlineTargets[s.declaringTypeFqn to s.methodName] = i
            }
        }
        if (inlineTargets.isEmpty()) return specs.indices.toList()

        val successors = Array(specs.size) { mutableSetOf<Int>() }
        var anyEdge = false
        for (a in specs.indices) {
            val host = hostMethodKey(specs[a]) ?: continue
            val b = inlineTargets[host] ?: continue
            if (a == b) continue
            successors[a].add(b)
            anyEdge = true
        }
        if (!anyEdge) return specs.indices.toList()

        // Kahn's algorithm. The ready set is kept sorted by original
        // index so unrelated specs keep their input order — stable.
        val inDegree = IntArray(specs.size)
        for (i in successors.indices) for (j in successors[i]) inDegree[j]++
        val ready = ArrayList<Int>()
        for (i in specs.indices) if (inDegree[i] == 0) ready += i
        ready.sort()

        val out = ArrayList<Int>(specs.size)
        while (ready.isNotEmpty()) {
            val i = ready.removeAt(0)
            out += i
            for (j in successors[i]) {
                inDegree[j]--
                if (inDegree[j] == 0) {
                    val pos = ready.binarySearch(j).let { if (it < 0) -it - 1 else it }
                    ready.add(pos, j)
                }
            }
        }
        // Cycle (impossible under "inline can't target its own host" but
        // defended for safety) → emit the input order unchanged.
        return if (out.size == specs.size) out else specs.indices.toList()
    }

    private fun hostMethodKey(spec: RefactoringSpec?): Pair<String, String>? = when (spec) {
        is RefactoringSpec.ExtractMethod -> spec.declaringTypeFqn to spec.hostMethodName
        is RefactoringSpec.ExtractVariable -> spec.declaringTypeFqn to spec.hostMethodName
        is RefactoringSpec.InlineVariable -> spec.declaringTypeFqn to spec.hostMethodName
        is RefactoringSpec.ExtractAttribute -> spec.declaringTypeFqn to spec.hostMethodName
        is RefactoringSpec.RenameLocalVariable -> spec.declaringTypeFqn to spec.hostMethodName
        is RefactoringSpec.RenameParameter -> spec.declaringTypeFqn to spec.hostMethodName
        is RefactoringSpec.ChangeVariableType -> spec.declaringTypeFqn to spec.hostMethodName
        is RefactoringSpec.ParameterizeVariable -> spec.declaringTypeFqn to spec.hostMethodName
        is RefactoringSpec.ParameterizeAttribute -> spec.declaringTypeFqn to spec.hostMethodName
        is RefactoringSpec.ReplaceVariableWithAttribute -> spec.declaringTypeFqn to spec.hostMethodName
        else -> null
    }
}
