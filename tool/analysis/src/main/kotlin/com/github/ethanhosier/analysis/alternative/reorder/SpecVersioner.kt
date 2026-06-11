package com.github.ethanhosier.analysis.alternative.reorder

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec

internal object SpecVersioner {

    data class Result(
        val effects: List<Effects>,
        val crossRangeEdges: List<CrossRangeEdge>,
    )

    data class CrossRangeEdge(
        val from: Int,
        val to: Int,
        val key: NameKey,
    )

    sealed interface NameKey {
        data class TypeKey(val fqn: String) : NameKey
        data class MethodKey(val declaringTypeFqn: String, val name: String) : NameKey
        data class FieldKey(val declaringTypeFqn: String, val name: String) : NameKey
        data class PackageKey(val name: String) : NameKey
    }

    fun version(specs: List<RefactoringSpec>): Result {
        val rawEffects = specs.map(::effectsOf)
        val live = HashMap<NameKey, Int>() // name → current producer trace index
        val rangesPerKey = HashMap<NameKey, MutableList<LiveRange>>()
        val versioned = ArrayList<Effects>(specs.size)

        for ((k, e) in rawEffects.withIndex()) {
            val producesV = e.produces.map { stampProduce(it, k, live, rangesPerKey) }.toSet()
            val readsV = e.reads.map { stampNonProduce(it, live) }.toSet()
            val writesV = e.writes.map { stampNonProduce(it, live) }.toSet()
            val consumesV = e.consumes.map { stampConsume(it, k, live, rangesPerKey) }.toSet()
            versioned.add(Effects(reads = readsV, writes = writesV, produces = producesV, consumes = consumesV))
        }

        val crossEdges = mutableListOf<CrossRangeEdge>()
        for ((key, ranges) in rangesPerKey) {
            for (i in 0 until ranges.size - 1) {
                val prev = ranges[i]
                val next = ranges[i + 1]
                val from = prev.consumerIdx ?: prev.producerIdx
                crossEdges.add(CrossRangeEdge(from = from, to = next.producerIdx, key = key))
            }
        }

        return Result(effects = versioned, crossRangeEdges = crossEdges)
    }

    private data class LiveRange(val producerIdx: Int, var consumerIdx: Int? = null)

    private fun stampProduce(
        e: Entity,
        traceIdx: Int,
        live: MutableMap<NameKey, Int>,
        ranges: MutableMap<NameKey, MutableList<LiveRange>>,
    ): Entity {
        val stamped = withVersion(e, traceIdx)
        val key = unversionedKey(e) ?: return stamped
        live[key] = traceIdx
        ranges.getOrPut(key) { mutableListOf() }.add(LiveRange(producerIdx = traceIdx))
        return stamped
    }

    private fun stampNonProduce(e: Entity, live: Map<NameKey, Int>): Entity {
        val key = unversionedKey(e) ?: return e
        val v = live[key] ?: 0
        return withVersion(e, v)
    }

    private fun stampConsume(
        e: Entity,
        traceIdx: Int,
        live: MutableMap<NameKey, Int>,
        ranges: MutableMap<NameKey, MutableList<LiveRange>>,
    ): Entity {
        val stamped = stampNonProduce(e, live)
        val key = unversionedKey(e) ?: return stamped
        // Close the most recent open range, if any.
        ranges[key]?.lastOrNull { it.consumerIdx == null }?.let { it.consumerIdx = traceIdx }
        live.remove(key)
        return stamped
    }

    private fun unversionedKey(e: Entity): NameKey? = when (e) {
        is Entity.Type -> NameKey.TypeKey(e.fqn)
        is Entity.Method -> NameKey.MethodKey(e.declaringTypeFqn, e.name)
        is Entity.Field -> NameKey.FieldKey(e.declaringTypeFqn, e.name)
        is Entity.Package -> NameKey.PackageKey(e.name)
        is Entity.HostMethodBody, is Entity.Declaration, is Entity.Region -> null
    }

    private fun withVersion(e: Entity, v: Int): Entity = when (e) {
        is Entity.Type -> e.copy(version = v)
        is Entity.Method -> e.copy(version = v)
        is Entity.Field -> e.copy(version = v)
        is Entity.Package -> e.copy(version = v)
        is Entity.HostMethodBody, is Entity.Declaration, is Entity.Region -> e
    }
}
