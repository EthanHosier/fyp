package com.github.ethanhosier.analysis.miner

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec

/**
 * Within a bracket (steps sharing the same `(fromSha, toSha)`), sort
 * specs so that any spec whose host method gets deleted by a sibling
 * `InlineMethod` runs *before* that inline.
 *
 * RM detects refactorings via static diff and anchors each spec to the
 * pre-state file. A user who in IntelliJ did `[InlineMethod
 * handleSilver, ExtractVariable t4 in priceOrder]` surfaces to us as
 * `[InlineMethod handleSilver, ExtractVariable hostMethodName=
 * handleSilver]` — the extract's host is the *pre-state* location, which
 * is inside the now-being-deleted method. Replaying in the natural
 * detection order deletes `handleSilver` before the extract has run;
 * doing the extract first works because the inline still pulls the
 * (now-extracted) body into the caller and the end-state AST matches.
 *
 * Pure function over the input list; output is a permutation. Stable on
 * the original order for spec pairs with no dependency between them.
 * Falls back to identity on cycles (shouldn't happen — would mean two
 * inlines of each other's hosts).
 */
object BracketSpecReorderer {

    /**
     * Returns the input list reordered so that all
     * `host-method-deleted-by-inline` edges are respected. Identity
     * permutation when no edges exist.
     */
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

        // Edge a → b ⇒ a must run before b. We add such an edge when
        // a's host method is the target of b's InlineMethod — b would
        // delete the host that a depends on.
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

    /**
     * `(declaringTypeFqn, hostMethodName)` for specs whose successful
     * apply requires that host method to still exist. Returns null for
     * class-/package-/inline-method specs that don't depend on a
     * specific host method's continued existence.
     */
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
