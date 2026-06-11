package com.github.ethanhosier.analysis.miner

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import kotlin.test.Test
import kotlin.test.assertEquals

class BracketSpecReordererTest {

    @Test
    fun `extract variable in a method that a sibling inline targets moves before the inline`() {
        val inline = RefactoringSpec.InlineMethod(
            declaringTypeFqn = "p.A",
            methodName = "handleSilver",
        )
        val extract = RefactoringSpec.ExtractVariable(
            relativeFilePath = "src/p/A.java",
            declaringTypeFqn = "p.A",
            hostMethodName = "handleSilver",
            hostMethodParamTypes = listOf("double"),
            selectionSubtreeHash = "h",
            selectionNodeCount = 1,
            newName = "t4",
        )
        assertEquals(listOf(1, 0), BracketSpecReorderer.reorder(listOf(inline, extract)))
    }

    @Test
    fun `independent specs keep their original order`() {
        val a = RefactoringSpec.RenameClass("p.A", "AA")
        val b = RefactoringSpec.RenameField("p.A", "x", "y")
        val c = RefactoringSpec.RenameMethod("p.A", "m", "mm")
        assertEquals(listOf(0, 1, 2), BracketSpecReorderer.reorder(listOf(a, b, c)))
    }

    @Test
    fun `inline whose host is not used by any sibling keeps stepIndex order`() {
        // The inline targets `dead`; no sibling has `dead` as a host
        // method, so there's no edge and we keep the input order.
        val inline = RefactoringSpec.InlineMethod("p.A", "dead")
        val unrelated = RefactoringSpec.ExtractVariable(
            relativeFilePath = "src/p/A.java",
            declaringTypeFqn = "p.A",
            hostMethodName = "alive",
            hostMethodParamTypes = emptyList(),
            selectionSubtreeHash = "h",
            selectionNodeCount = 1,
            newName = "v",
        )
        assertEquals(listOf(0, 1), BracketSpecReorderer.reorder(listOf(inline, unrelated)))
    }

    @Test
    fun `host on a different declaring type does not match the inline`() {
        // Same method name but different declaring type → no edge.
        val inline = RefactoringSpec.InlineMethod("p.A", "m")
        val extract = RefactoringSpec.ExtractVariable(
            relativeFilePath = "src/p/B.java",
            declaringTypeFqn = "p.B",
            hostMethodName = "m",
            hostMethodParamTypes = emptyList(),
            selectionSubtreeHash = "h",
            selectionNodeCount = 1,
            newName = "v",
        )
        assertEquals(listOf(0, 1), BracketSpecReorderer.reorder(listOf(inline, extract)))
    }

    @Test
    fun `unrelated rename between dependent pair preserves relative order`() {
        val inline = RefactoringSpec.InlineMethod("p.A", "m")
        val rename = RefactoringSpec.RenameClass("p.A", "AA")
        val extract = RefactoringSpec.ExtractVariable(
            relativeFilePath = "src/p/A.java",
            declaringTypeFqn = "p.A",
            hostMethodName = "m",
            hostMethodParamTypes = emptyList(),
            selectionSubtreeHash = "h",
            selectionNodeCount = 1,
            newName = "v",
        )
        assertEquals(listOf(1, 2, 0), BracketSpecReorderer.reorder(listOf(inline, rename, extract)))
    }

    @Test
    fun `singleton or empty bracket is identity`() {
        assertEquals(emptyList(), BracketSpecReorderer.reorder(emptyList()))
        val one = RefactoringSpec.InlineMethod("p.A", "x")
        assertEquals(listOf(0), BracketSpecReorderer.reorder(listOf(one)))
    }
}
