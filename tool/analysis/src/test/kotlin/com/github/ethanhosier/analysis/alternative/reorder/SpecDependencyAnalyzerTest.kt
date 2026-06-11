package com.github.ethanhosier.analysis.alternative.reorder

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpecDependencyAnalyzerTest {

    @Test
    fun `independent renames on different classes have no edge`() {
        val a = RefactoringSpec.RenameClass(typeFqn = "com.A", newName = "AA")
        val b = RefactoringSpec.RenameClass(typeFqn = "com.B", newName = "BB")
        val dag = SpecDependencyAnalyzer.analyze(listOf(a, b))
        assertEquals(emptyMap(), dag.edges)
    }

    @Test
    fun `rename method then move on the renamed name chains`() {
        val rename = RefactoringSpec.RenameMethod(
            declaringTypeFqn = "com.Foo",
            oldName = "old",
            newName = "fresh",
            paramTypeSignatures = emptyList(),
        )
        val move = RefactoringSpec.MoveInstanceMethod(
            sourceTypeFqn = "com.Foo",
            methodName = "fresh",
            targetName = "delegate",
        )
        val dag = SpecDependencyAnalyzer.analyze(listOf(rename, move))
        assertContains(dag.successors(0), 1)
    }

    @Test
    fun `extract method then rename of extracted method chains via opaque match`() {
        val extract = RefactoringSpec.ExtractMethod(
            relativeFilePath = "F.java",
            declaringTypeFqn = "com.Foo",
            hostMethodName = "run",
            hostMethodParamTypes = emptyList(),
            selectionSubtreeHash = "h",
            selectionNodeCount = 1,
            newMethodName = "extracted",
        )
        val rename = RefactoringSpec.RenameMethod(
            declaringTypeFqn = "com.Foo",
            oldName = "extracted",
            newName = "renamed",
            paramTypeSignatures = listOf("int"), // unknown when extract ran — opaque match
        )
        val dag = SpecDependencyAnalyzer.analyze(listOf(extract, rename))
        assertContains(dag.successors(0), 1)
    }

    @Test
    fun `two extracts in same host body do not auto-conflict (optimistic model)`() {
        val a = extractMethod("run", "h1", "a")
        val b = extractMethod("run", "h2", "b")
        val dag = SpecDependencyAnalyzer.analyze(listOf(a, b))
        assertEquals(emptyMap(), dag.edges)
    }

    @Test
    fun `rename inside a host body still chains into a later extract on that host`() {
        val rename = RefactoringSpec.RenameLocalVariable(
            relativeFilePath = "F.java",
            declaringTypeFqn = "com.Foo",
            hostMethodName = "run",
            hostMethodParamTypes = emptyList(),
            declarationSubtreeHash = "v1",
            newName = "renamed",
        )
        val extract = extractMethod("run", "sel", "extracted")
        val dag = SpecDependencyAnalyzer.analyze(listOf(rename, extract))
        assertContains(dag.successors(0), 1)
    }

    @Test
    fun `two extracts in disjoint host methods have no edge`() {
        val a = extractMethod("runA", "h1", "a")
        val b = extractMethod("runB", "h2", "b")
        val dag = SpecDependencyAnalyzer.analyze(listOf(a, b))
        assertEquals(emptyMap(), dag.edges)
    }

    @Test
    fun `inline method of one method commutes with rename of unrelated method on same class`() {
        val inline = RefactoringSpec.InlineMethod(
            declaringTypeFqn = "com.Foo",
            methodName = "h",
            paramTypeSignatures = emptyList(),
        )
        val rename = RefactoringSpec.RenameMethod(
            declaringTypeFqn = "com.Foo",
            oldName = "other",
            newName = "renamed",
            paramTypeSignatures = emptyList(),
        )
        val dag = SpecDependencyAnalyzer.analyze(listOf(inline, rename))
        assertEquals(emptyMap(), dag.edges)
    }

    @Test
    fun `parameterize variable then call-site rename chains via Type write`() {
        val parameterize = RefactoringSpec.ParameterizeVariable(
            relativeFilePath = "F.java",
            declaringTypeFqn = "com.Foo",
            hostMethodName = "run",
            hostMethodParamTypes = emptyList(),
            selectionSubtreeHash = "h",
            selectionNodeCount = 1,
            newParameterName = "p",
        )
        val rename = RefactoringSpec.RenameMethod(
            declaringTypeFqn = "com.Foo",
            oldName = "other",
            newName = "renamed",
            paramTypeSignatures = emptyList(),
        )
        val dag = SpecDependencyAnalyzer.analyze(listOf(parameterize, rename))
        assertContains(dag.successors(0), 1)
    }

    @Test
    fun `user observed ordering is always emitted`() {
        val specs = listOf(
            RefactoringSpec.RenameClass(typeFqn = "com.A", newName = "AA"),
            extractMethod("run", "h1", "a"),
            RefactoringSpec.RenameField(declaringTypeFqn = "com.A", oldName = "x", newName = "y"),
        )
        val dag = SpecDependencyAnalyzer.analyze(specs)
        val result = TopologicalEnumerator.enumerate(dag)
        assertTrue(specs.indices.toList() in result.orderings)
    }

    @Test
    fun `analyzer rejects RefactoringSpec_Other`() {
        assertThrows<IllegalArgumentException> {
            SpecDependencyAnalyzer.analyze(listOf(RefactoringSpec.Other))
        }
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
}
