package com.github.ethanhosier.analysis.alternative.reorder

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec

/**
 * Directed acyclic graph over a window's mined refactoring specs.
 *
 * Edge `i → j` (i = predecessor) means `nodes[j]` cannot be applied
 * before `nodes[i]` — they conflict on at least one [Entity].
 *
 * Built by [SpecDependencyAnalyzer]. Node order matches the input
 * order (the user's observed trace), and edges always go forward in
 * input order, so the input ordering is by construction a valid
 * topological sort.
 */
data class SpecDag(
    val nodes: List<RefactoringSpec>,
    val edges: Map<Int, Set<Int>>,
    /** Per-edge derivation reasons — for [ReorderDebug]. */
    val edgeReasons: Map<Pair<Int, Int>, List<EdgeReason>> = emptyMap(),
) {
    fun successors(i: Int): Set<Int> = edges[i].orEmpty()
    fun predecessors(i: Int): Set<Int> =
        edges.entries.asSequence().filter { i in it.value }.map { it.key }.toSet()
}

/**
 * Why an edge was added — surfaces in [ReorderDebug.describe] so a
 * human reviewer can spot spurious or missing edges.
 */
data class EdgeReason(
    val kind: Kind,
    val entity: Entity,
) {
    enum class Kind {
        /** j reads something i produces or writes. */
        READ_AFTER_WRITE,
        /** j reads something i consumed. */
        READ_AFTER_CONSUME,
        /** j writes something i wrote or consumed. */
        WRITE_AFTER_WRITE,
        /** j and i both consume the same entity. */
        CONSUME_AFTER_CONSUME,
        /**
         * j produces an un-versioned name that i (a consumer of the
         * previous version) closed off. Required so two productions
         * of the same logical name can't both be live simultaneously
         * in any valid ordering.
         */
        PRODUCES_AFTER_CONSUME,
    }
}

object SpecDependencyAnalyzer {

    /**
     * Build the DAG.
     *
     * Throws if any spec is [RefactoringSpec.Other] — callers must
     * filter those (the analyser can't reason about untyped detections).
     */
    fun analyze(specs: List<RefactoringSpec>): SpecDag {
        require(specs.none { it == RefactoringSpec.Other }) {
            "SpecDependencyAnalyzer cannot reason about RefactoringSpec.Other; caller must filter"
        }

        // SSA pre-pass: stamp each named entity (Type / Method / Field /
        // Package) with the trace index of its current producer. Same
        // logical name produced multiple times in the window becomes
        // multiple distinct versioned entities, so pairwise overlap
        // naturally distinguishes them.
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

        // Cross-range edges: two productions of the same un-versioned
        // name can't both be live simultaneously, so the consumer that
        // closed range k must precede the producer that opens range
        // k+1. Use a synthetic [Entity.Type]-flavoured EdgeReason so
        // the debug formatter can render which name triggered it.
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

    /** Render a [SpecVersioner.NameKey] as an [Entity] for [EdgeReason] display. */
    private fun nameKeyMarker(key: SpecVersioner.NameKey): Entity = when (key) {
        is SpecVersioner.NameKey.TypeKey -> Entity.Type(key.fqn)
        is SpecVersioner.NameKey.MethodKey -> Entity.Method(key.declaringTypeFqn, key.name, ParamTypes.Opaque())
        is SpecVersioner.NameKey.FieldKey -> Entity.Field(key.declaringTypeFqn, key.name)
        is SpecVersioner.NameKey.PackageKey -> Entity.Package(key.name)
    }

    /**
     * Structural match between two entities, with an Opaque-paramTypes
     * carve-out for [Entity.Method].
     *
     * Two methods match if:
     *  - their (declaringTypeFqn, name) agree, AND
     *  - their paramTypeSignatures are equal, OR either side is opaque.
     *
     * Lets a later RenameMethod find a previously-extracted method by
     * name without us needing to guess the new method's param list.
     * For all other [Entity] types: standard structural equality.
     */
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
