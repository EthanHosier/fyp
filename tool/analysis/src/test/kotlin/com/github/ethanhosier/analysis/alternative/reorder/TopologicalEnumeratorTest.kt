package com.github.ethanhosier.analysis.alternative.reorder

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TopologicalEnumeratorTest {

    @Test
    fun `empty dag yields a single empty ordering`() {
        val dag = SpecDag(nodes = emptyList(), edges = emptyMap())
        val r = TopologicalEnumerator.enumerate(dag)
        assertEquals(listOf(emptyList<Int>()), r.orderings)
        assertEquals(false, r.truncated)
    }

    @Test
    fun `three independent nodes yield 6 orderings`() {
        val dag = SpecDag(
            nodes = List(3) { RefactoringSpec.RenameClass("com.X$it", "Y$it") },
            edges = emptyMap(),
        )
        val r = TopologicalEnumerator.enumerate(dag)
        assertEquals(6, r.orderings.size)
        assertEquals(r.orderings.toSet().size, r.orderings.size, "no duplicates")
    }

    @Test
    fun `linear chain yields one ordering`() {
        val dag = SpecDag(
            nodes = List(4) { RefactoringSpec.RenameClass("com.X$it", "Y$it") },
            edges = mapOf(0 to setOf(1), 1 to setOf(2), 2 to setOf(3)),
        )
        val r = TopologicalEnumerator.enumerate(dag)
        assertEquals(listOf(listOf(0, 1, 2, 3)), r.orderings)
    }

    @Test
    fun `diamond dag yields two orderings`() {
        val dag = SpecDag(
            nodes = List(4) { RefactoringSpec.RenameClass("com.X$it", "Y$it") },
            edges = mapOf(0 to setOf(1, 2), 1 to setOf(3), 2 to setOf(3)),
        )
        val r = TopologicalEnumerator.enumerate(dag)
        assertEquals(setOf(listOf(0, 1, 2, 3), listOf(0, 2, 1, 3)), r.orderings.toSet())
    }

    @Test
    fun `nodes above maxNodes returns truncated with skipReason`() {
        val dag = SpecDag(
            nodes = List(8) { RefactoringSpec.RenameClass("com.X$it", "Y$it") },
            edges = emptyMap(),
        )
        val r = TopologicalEnumerator.enumerate(dag)
        assertTrue(r.orderings.isEmpty())
        assertTrue(r.truncated)
        assertNotNull(r.skipReason)
    }

    @Test
    fun `orderings above maxOrderings truncate with no skipReason`() {
        val dag = SpecDag(
            nodes = List(7) { RefactoringSpec.RenameClass("com.X$it", "Y$it") },
            edges = emptyMap(),
        )
        val r = TopologicalEnumerator.enumerate(dag, EnumerationBudget(maxNodes = 7, maxOrderings = 100))
        assertEquals(100, r.orderings.size)
        assertTrue(r.truncated)
        assertNull(r.skipReason)
    }
}
