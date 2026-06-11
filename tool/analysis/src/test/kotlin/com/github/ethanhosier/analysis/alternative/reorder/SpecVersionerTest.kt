package com.github.ethanhosier.analysis.alternative.reorder

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpecVersionerTest {

    @Test
    fun `extract — inline — re-extract — rename forms a single linear chain`() {
        val specs = listOf(
            extractMethod("render", "h0", "helper"),            // [0] produces helper@v0
            inlineMethod("helper"),                              // [1] consumes helper@v0
            extractMethod("render", "h1", "helper"),            // [2] produces helper@v2
            renameMethod(old = "helper", new = "renderHeader"),  // [3] reads/consumes helper@v2
        )

        val dag = SpecDependencyAnalyzer.analyze(specs)
        val orderings = TopologicalEnumerator.enumerate(dag).orderings
        assertEquals(listOf(listOf(0, 1, 2, 3)), orderings)

        // Cross-range edge specifically: 1 → 2 with PRODUCES_AFTER_CONSUME
        // (the inline closed v=0; the second extract opens v=2).
        val reasons12 = dag.edgeReasons[1 to 2].orEmpty()
        assertTrue(
            reasons12.any { it.kind == EdgeReason.Kind.PRODUCES_AFTER_CONSUME },
            "expected cross-range edge between inline (1) and re-extract (2); got $reasons12",
        )
    }

    @Test
    fun `re-creating a name without an intervening consume preserves user order`() {
        val specs = listOf(
            extractMethod("render", "h0", "helper"),
            extractMethod("render", "h1", "helper"),
        )
        val dag = SpecDependencyAnalyzer.analyze(specs)
        assertContains(dag.successors(0), 1)
        assertEquals(listOf(listOf(0, 1)), TopologicalEnumerator.enumerate(dag).orderings)
    }

    @Test
    fun `versions distinguish two productions of the same method name`() {
        val specs = listOf(
            extractMethod("render", "h0", "helper"),
            inlineMethod("helper"),
            extractMethod("render", "h1", "helper"),
        )
        val ssa = SpecVersioner.version(specs)

        // Step 0 produced helper @ v=0 (the producer's trace index).
        val produced0 = ssa.effects[0].produces.filterIsInstance<Entity.Method>().single()
        assertEquals(0, produced0.version)
        assertEquals("helper", produced0.name)

        // Step 1 resolved its consume to the same v=0 (live at that point).
        val consumed1 = ssa.effects[1].consumes.filterIsInstance<Entity.Method>().single()
        assertEquals(0, consumed1.version)

        // Step 2 produced helper @ v=2 — a fresh version distinct from v=0.
        val produced2 = ssa.effects[2].produces.filterIsInstance<Entity.Method>().single()
        assertEquals(2, produced2.version)
        assertEquals("helper", produced2.name)
    }

    @Test
    fun `cross-range edges are exposed on the versioner result`() {
        val specs = listOf(
            extractMethod("render", "h0", "helper"),
            inlineMethod("helper"),
            extractMethod("render", "h1", "helper"),
        )
        val ssa = SpecVersioner.version(specs)

        // One range pair → one cross-range edge: from inline (1) to
        // re-extract (2), keyed on Method(com.Foo, helper).
        assertEquals(1, ssa.crossRangeEdges.size)
        val edge = ssa.crossRangeEdges.single()
        assertEquals(1, edge.from)
        assertEquals(2, edge.to)
        assertEquals(SpecVersioner.NameKey.MethodKey("com.Foo", "helper"), edge.key)
    }

    @Test
    fun `no cross-range edges when each name is produced at most once`() {
        val specs = listOf(
            RefactoringSpec.RenameClass(typeFqn = "com.Foo", newName = "Bar"),
            RefactoringSpec.RenameClass(typeFqn = "com.Baz", newName = "BazV2"),
        )
        val ssa = SpecVersioner.version(specs)
        assertEquals(emptyList(), ssa.crossRangeEdges)
    }

    @Test
    fun `single production gets version equal to its producer's trace index`() {
        val specs = listOf(
            RefactoringSpec.RenameClass(typeFqn = "com.Foo", newName = "Bar"),
        )
        val ssa = SpecVersioner.version(specs)
        val produced = ssa.effects[0].produces.filterIsInstance<Entity.Type>().single()
        assertEquals(0, produced.version)
        assertEquals("com.Bar", produced.fqn)
    }

    @Test
    fun `host-anchored entities — Region, HostMethodBody, Declaration — are not versioned`() {
        val specs = listOf(extractMethod("render", "h0", "helper"))
        val ssa = SpecVersioner.version(specs)
        // ExtractMethod's reads include the Region and the HostMethodBody.
        // Both should pass through untouched (no `version` field on those).
        val reads = ssa.effects[0].reads
        assertTrue(reads.any { it is Entity.Region })
        assertTrue(reads.any { it is Entity.HostMethodBody })
    }

    private fun extractMethod(host: String, sel: String, newName: String) =
        RefactoringSpec.ExtractMethod(
            relativeFilePath = "F.java",
            declaringTypeFqn = "com.Foo",
            hostMethodName = host,
            hostMethodParamTypes = emptyList(),
            selectionSubtreeHash = sel,
            selectionNodeCount = 1,
            newMethodName = newName,
        )

    private fun inlineMethod(name: String) =
        RefactoringSpec.InlineMethod(
            declaringTypeFqn = "com.Foo",
            methodName = name,
            paramTypeSignatures = emptyList(),
        )

    private fun renameMethod(old: String, new: String) =
        RefactoringSpec.RenameMethod(
            declaringTypeFqn = "com.Foo",
            oldName = old,
            newName = new,
            paramTypeSignatures = emptyList(),
        )
}
