package com.github.ethanhosier.analysis.refactoring.anchor

import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.dom.ASTNode
import org.eclipse.jdt.core.dom.ASTParser
import org.eclipse.jdt.core.dom.Block
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.core.dom.MethodDeclaration
import org.eclipse.jdt.core.dom.TypeDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AstSubtreeHasherTest {

    @Test
    fun `hash of identical blocks is equal`() {
        val a = blockOf("""
            int x = 1;
            int y = 2;
            return x + y;
        """.trimIndent())
        val b = blockOf("""
            int x = 1;
            int y = 2;
            return x + y;
        """.trimIndent())
        assertEquals(AstSubtreeHasher.hashNodes(a), AstSubtreeHasher.hashNodes(b))
    }

    @Test
    fun `hash invariant to whitespace`() {
        val a = blockOf("int x = 1; int y = 2; return x + y;")
        val b = blockOf("""
            int x  =  1;

                int y =   2;
            return x +  y;
        """.trimIndent())
        assertEquals(AstSubtreeHasher.hashNodes(a), AstSubtreeHasher.hashNodes(b))
    }

    @Test
    fun `hash invariant to comments and javadoc`() {
        val a = methodBody("""
            int x = 1;
            return x;
        """.trimIndent(), javadoc = null)
        val b = methodBody("""
            // explanatory comment
            int x = 1;
            /* another */
            return x;
        """.trimIndent(), javadoc = "/** doc */")
        assertEquals(AstSubtreeHasher.hashNodes(a), AstSubtreeHasher.hashNodes(b))
    }

    @Test
    fun `hash changes when literal changes`() {
        val a = blockOf("""log("a");""")
        val b = blockOf("""log("b");""")
        assertNotEquals(AstSubtreeHasher.hashNodes(a), AstSubtreeHasher.hashNodes(b))
    }

    @Test
    fun `hash changes on identifier rename`() {
        val a = blockOf("log.info(msg);")
        val b = blockOf("logger.info(msg);")
        assertNotEquals(AstSubtreeHasher.hashNodes(a), AstSubtreeHasher.hashNodes(b))
    }

    @Test
    fun `hash changes on structural reshape`() {
        val a = blockOf("if (x) { return 1; } return 2;")
        val b = blockOf("if (x) return 1; return 2;")
        // Block vs single-statement-then-clause is a real structural
        // change (Block node added) — should affect the hash.
        assertNotEquals(AstSubtreeHasher.hashNodes(a), AstSubtreeHasher.hashNodes(b))
    }

    private fun blockOf(statements: String): List<ASTNode> {
        val body = methodBody(statements, javadoc = null)
        return body
    }

    private fun methodBody(statements: String, javadoc: String?): List<ASTNode> {
        val source = """
            ${javadoc ?: ""}
            class T {
                Object log;
                Object logger;
                boolean x;
                String msg;
                Object m() {
                    $statements
                }
                void log(Object o) {}
            }
        """.trimIndent()
        val parser = ASTParser.newParser(AST.JLS_Latest).apply {
            setKind(ASTParser.K_COMPILATION_UNIT)
            setSource(source.toCharArray())
            setResolveBindings(false)
        }
        val cu = parser.createAST(null) as CompilationUnit
        val type = cu.types().first() as TypeDeclaration
        val method = type.methods.first { it.name.identifier == "m" } as MethodDeclaration
        val block = method.body as Block
        @Suppress("UNCHECKED_CAST")
        return (block.statements() as List<ASTNode>).toList()
    }
}
