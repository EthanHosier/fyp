package com.github.ethanhosier.analysis.alternative.reorder

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec

data class SpecDag(
    val nodes: List<RefactoringSpec>,
    val edges: Map<Int, Set<Int>>,
    val edgeReasons: Map<Pair<Int, Int>, List<EdgeReason>> = emptyMap(),
) {
    fun successors(i: Int): Set<Int> = edges[i].orEmpty()
    fun predecessors(i: Int): Set<Int> =
        edges.entries.asSequence().filter { i in it.value }.map { it.key }.toSet()
}

data class EdgeReason(
    val kind: Kind,
    val entity: Entity,
) {
    enum class Kind {
        READ_AFTER_WRITE,
        READ_AFTER_CONSUME,
        WRITE_AFTER_WRITE,
        CONSUME_AFTER_CONSUME,
        PRODUCES_AFTER_CONSUME,
    }
}

object SpecDependencyAnalyzer {

    fun analyze(specs: List<RefactoringSpec>): SpecDag {
        require(specs.none { it == RefactoringSpec.Other }) {
            "SpecDependencyAnalyzer cannot reason about RefactoringSpec.Other; caller must filter"
        }

        val ssa = SpecVersioner.version(specs)
        val effects = ssa.effects
        val edges = HashMap<Int, MutableSet<Int>>()
        val reasons = HashMap<Pair<Int, Int>, MutableList<EdgeReason>>()

        for (j in specs.indices) {
            for (i in 0 until j) {
                val why = mutableListOf<EdgeReason>()

                effects[j].reads.forEach { rj ->
                    effects[i].produces.firstOrNull { matches(it, rj) }?.let {
                        why += EdgeReason(EdgeReason.Kind.READ_AFTER_WRITE, it)
                    }
                    effects[i].writes.firstOrNull { matches(it, rj) }?.let {
                        why += EdgeReason(EdgeReason.Kind.READ_AFTER_WRITE, it)
                    }
                    effects[i].consumes.firstOrNull { matches(it, rj) }?.let {
                        why += EdgeReason(EdgeReason.Kind.READ_AFTER_CONSUME, it)
                    }
                }
                effects[j].writes.forEach { wj ->
                    effects[i].writes.firstOrNull { matches(it, wj) }?.let {
                        why += EdgeReason(EdgeReason.Kind.WRITE_AFTER_WRITE, it)
                    }
                    effects[i].consumes.firstOrNull { matches(it, wj) }?.let {
                        why += EdgeReason(EdgeReason.Kind.WRITE_AFTER_WRITE, it)
                    }
                }
                effects[j].consumes.forEach { cj ->
                    effects[i].consumes.firstOrNull { matches(it, cj) }?.let {
                        why += EdgeReason(EdgeReason.Kind.CONSUME_AFTER_CONSUME, it)
                    }
                }

                if (why.isNotEmpty()) {
                    edges.getOrPut(i) { mutableSetOf() }.add(j)
                    reasons[i to j] = why
                }
            }
        }

        for (cre in ssa.crossRangeEdges) {
            val newEdge = edges.getOrPut(cre.from) { mutableSetOf() }.add(cre.to)
            val markerEntity = nameKeyMarker(cre.key)
            val reason = EdgeReason(EdgeReason.Kind.PRODUCES_AFTER_CONSUME, markerEntity)
            reasons.getOrPut(cre.from to cre.to) { mutableListOf() }.add(reason)
            // newEdge being false (already there) is fine — we still
            // record the additional reason for that pair.
            @Suppress("UNUSED_VARIABLE") val unused = newEdge
        }

        return SpecDag(
            nodes = specs,
            edges = edges.mapValues { it.value.toSet() },
            edgeReasons = reasons.mapValues { it.value.toList() },
        )
    }

    private fun nameKeyMarker(key: SpecVersioner.NameKey): Entity = when (key) {
        is SpecVersioner.NameKey.TypeKey -> Entity.Type(key.fqn)
        is SpecVersioner.NameKey.MethodKey -> Entity.Method(key.declaringTypeFqn, key.name, ParamTypes.Opaque())
        is SpecVersioner.NameKey.FieldKey -> Entity.Field(key.declaringTypeFqn, key.name)
        is SpecVersioner.NameKey.PackageKey -> Entity.Package(key.name)
    }

    private fun matches(a: Entity, b: Entity): Boolean {
        if (a is Entity.Method && b is Entity.Method) {
            if (a.declaringTypeFqn != b.declaringTypeFqn || a.name != b.name) return false
            // SSA: distinct versions are different entities.
            if (a.version != b.version) return false
            val ap = a.paramTypeSignatures; val bp = b.paramTypeSignatures
            return when {
                ap is ParamTypes.Opaque || bp is ParamTypes.Opaque -> true
                ap is ParamTypes.Known && bp is ParamTypes.Known -> ap.list == bp.list
                else -> false
            }
        }
        // Type / Field / Package equality already includes their `version`
        // field, so structurally distinct versions don't collide here.
        return a == b
    }
}
