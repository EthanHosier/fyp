package com.github.ethanhosier.analysis.alternative.reorder

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpecEffectsTest {

    @Test
    fun `RenameMethod produces new method, consumes old, writes declaring type`() {
        val s = RefactoringSpec.RenameMethod(
            declaringTypeFqn = "com.foo.Bar",
            oldName = "doIt",
            newName = "process",
            paramTypeSignatures = listOf("int"),
        )
        val e = effectsOf(s)
        assertContains(e.consumes, Entity.Method("com.foo.Bar", "doIt", ParamTypes.Known(listOf("int"))))
        assertContains(e.produces, Entity.Method("com.foo.Bar", "process", ParamTypes.Known(listOf("int"))))
        assertContains(e.writes, Entity.Type("com.foo.Bar"))
    }

    @Test
    fun `RenameClass swaps Type entity in same package`() {
        val s = RefactoringSpec.RenameClass(typeFqn = "com.foo.Bar", newName = "Baz")
        val e = effectsOf(s)
        assertContains(e.consumes, Entity.Type("com.foo.Bar"))
        assertContains(e.produces, Entity.Type("com.foo.Baz"))
    }

    @Test
    fun `ExtractMethod reads host body and region, produces opaque-param method, no writes (optimistic)`() {
        val s = RefactoringSpec.ExtractMethod(
            relativeFilePath = "Foo.java",
            declaringTypeFqn = "com.foo.Bar",
            hostMethodName = "run",
            hostMethodParamTypes = emptyList(),
            selectionSubtreeHash = "deadbeef",
            selectionNodeCount = 2,
            newMethodName = "extracted",
        )
        val e = effectsOf(s)
        val host = Entity.HostMethodBody("com.foo.Bar", "run", emptyList())
        // Reads host (so prior body-mutating ops chain into us) and the region itself.
        assertContains(e.reads, host)
        assertContains(e.reads, Entity.Region(host, "deadbeef"))
        // Optimistic model: extracts deliberately don't write host — see
        // KDoc on `effectsOf`. Two extracts on the same host commute.
        assertTrue(host !in e.writes)
        // Produced method's params are opaque; matches by (type, name).
        val produced = e.produces.filterIsInstance<Entity.Method>().single()
        assertEquals("com.foo.Bar", produced.declaringTypeFqn)
        assertEquals("extracted", produced.name)
        assertTrue(produced.paramTypeSignatures is ParamTypes.Opaque)
    }

    @Test
    fun `RenameLocalVariable consumes the declaration and writes host body`() {
        val s = RefactoringSpec.RenameLocalVariable(
            relativeFilePath = "Foo.java",
            declaringTypeFqn = "com.foo.Bar",
            hostMethodName = "run",
            hostMethodParamTypes = emptyList(),
            declarationSubtreeHash = "abc",
            newName = "tmp",
        )
        val e = effectsOf(s)
        val host = Entity.HostMethodBody("com.foo.Bar", "run", emptyList())
        assertContains(e.consumes, Entity.Declaration(host, "abc"))
        assertContains(e.writes, host)
    }

    @Test
    fun `InlineMethod consumes named method without coarsely writing declaring type`() {
        val s = RefactoringSpec.InlineMethod(
            declaringTypeFqn = "com.foo.Bar",
            methodName = "helper",
            paramTypeSignatures = emptyList(),
        )
        val e = effectsOf(s)
        assertEquals(emptySet(), e.writes)
        assertContains(e.consumes, Entity.Method("com.foo.Bar", "helper", ParamTypes.Known(emptyList())))
    }

    @Test
    fun `MoveInstanceField consumes source field and produces destination field`() {
        val s = RefactoringSpec.MoveInstanceField(
            sourceTypeFqn = "com.foo.Bar",
            fieldName = "count",
            destinationTypeFqn = "com.foo.Baz",
        )
        val e = effectsOf(s)
        assertContains(e.consumes, Entity.Field("com.foo.Bar", "count"))
        assertContains(e.produces, Entity.Field("com.foo.Baz", "count"))
    }

    @Test
    fun `Other throws — caller must filter`() {
        assertThrows<IllegalStateException> { effectsOf(RefactoringSpec.Other) }
    }
}
