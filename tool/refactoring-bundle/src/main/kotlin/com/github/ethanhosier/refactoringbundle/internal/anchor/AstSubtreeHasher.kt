package com.github.ethanhosier.refactoringbundle.internal.anchor

import org.eclipse.jdt.core.dom.ASTNode
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor
import org.eclipse.jdt.core.dom.ChildPropertyDescriptor
import org.eclipse.jdt.core.dom.Comment
import org.eclipse.jdt.core.dom.MethodDeclaration
import org.eclipse.jdt.core.dom.SimplePropertyDescriptor
import org.eclipse.jdt.core.dom.SingleVariableDeclaration
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor
import java.security.MessageDigest

/**
 * Content-addressable hash of a JDT AST subtree.
 *
 * Walks structural properties in JDT's declared order, encoding node
 * type, simple-property values, and recursing into child / child-list
 * properties. Comments / Javadoc are skipped — they are AST nodes but
 * carry no semantic information for refactoring identity. Source
 * positions are *not* structural properties in JDT, so they don't
 * affect the hash; whitespace and line shifts above the region are
 * therefore invisible.
 *
 * ## Method-list canonicalisation — why this exists
 *
 * **Problem.** The reorder/synthesis slice replays a refactoring
 * window in different orders to find equivalent alternative
 * trajectories. Two Extract Methods applied as `R1, R2` vs `R2, R1`
 * produce the **same set of methods** but in opposite declaration
 * order inside the host class — class body `[m1, m2]` vs `[m2, m1]`.
 * Without canonicalisation, the per-file AST hash differs between
 * the two outcomes, so [com.github.ethanhosier.analysis.alternative.validate.RefactoringStepValidator]
 * (and any cache keyed on the file hash) marks the alt ordering as
 * `AST_DIVERGED` purely on cosmetic differences, even though Java
 * doesn't observe method declaration order at all.
 *
 * **Fix.** When emitting a `bodyDeclarations` list, sort
 * [MethodDeclaration] entries by `name(paramTypes)`. Non-method
 * members keep source order. See [canonicallyOrderBodyDecls].
 *
 * **Why this scope and not a general "sort all order-irrelevant
 * sibling lists" pass.** Java has very few sibling lists that are
 * truly order-irrelevant. Methods are; most everything else isn't.
 * Fields with cross-referencing initialisers depend on declaration
 * order; `static {}` blocks run in source order; statements inside
 * a method body are sequential by definition; switch cases
 * fall-through; enum constants have ordinals. A broad normalisation
 * would silently collapse trees whose semantics actually differ.
 * Allowlisting the one case that comes up in practice (Extract
 * Method's effect on a class body) gets the win without the risk.
 *
 * **Why we accept method overload-resolution corner cases.** The
 * sort key uses `type.toString()` from each parameter's
 * `SingleVariableDeclaration`, which is the source-level type name.
 * Within a single tree being hashed all keys come from the same
 * source so they sort consistently; across two trees being compared
 * for equivalence, if the parameter types differ in source form
 * (e.g. `List<String>` vs `java.util.List<String>`) the per-method
 * hash already differs through the simple-property `type` value, so
 * a divergent sort can't accidentally produce a false hash match.
 *
 * Documented here so that future maintainers (or future-me) hitting
 * "why does the validator pass for what looks like a different
 * file?" can find the reasoning without spelunking git history.
 *
 * **Must stay byte-identical** to the analysis-side copy at
 * `analysis/.../refactoring/anchor/AstSubtreeHasher.kt`. A shared test
 * fixture (a Java snippet + its expected SHA) is run on both sides;
 * any drift fails CI.
 */
internal object AstSubtreeHasher {

    fun hashNode(node: ASTNode): String = sha256Hex(canonical(node))

    /** Hash an ordered list of sibling AST nodes (e.g. a contiguous
     *  statement window inside a method body). */
    fun hashNodes(nodes: List<ASTNode>): String {
        val sb = StringBuilder()
        sb.append("[seq:").append(nodes.size).append(']')
        for (n in nodes) {
            sb.append('(')
            emit(n, sb)
            sb.append(')')
        }
        return sha256Hex(sb.toString())
    }

    private fun canonical(node: ASTNode): String = StringBuilder().also { emit(node, it) }.toString()

    private fun emit(node: ASTNode, sb: StringBuilder) {
        if (node is Comment) return
        sb.append(node.nodeType)
        for (prop in node.structuralPropertiesForType().filterIsInstance<StructuralPropertyDescriptor>()) {
            val value = node.getStructuralProperty(prop) ?: continue
            when (prop) {
                is SimplePropertyDescriptor -> {
                    sb.append('|').append(prop.id).append('=').append(value.toString())
                }
                is ChildPropertyDescriptor -> {
                    if (value is Comment) continue
                    sb.append('(')
                    emit(value as ASTNode, sb)
                    sb.append(')')
                }
                is ChildListPropertyDescriptor -> {
                    sb.append('[')
                    val list = value as List<*>
                    val ordered = if (prop.id == "bodyDeclarations") canonicallyOrderBodyDecls(list) else list
                    for (child in ordered) {
                        if (child is ASTNode && child !is Comment) {
                            sb.append('(')
                            emit(child, sb)
                            sb.append(')')
                        }
                    }
                    sb.append(']')
                }
            }
        }
    }

    /**
     * Canonicalise a `bodyDeclarations` list (on any
     * [org.eclipse.jdt.core.dom.AbstractTypeDeclaration] or
     * [org.eclipse.jdt.core.dom.AnonymousClassDeclaration]) by
     * sorting [MethodDeclaration] entries by `name(paramTypes)` while
     * keeping every non-method entry in source order.
     *
     * **Why methods get sorted.** Two Extract Method refactorings
     * applied in opposite orders produce the same set of methods in
     * different declaration positions. Java doesn't observe method
     * declaration order, so the validator (and any hash-keyed cache)
     * shouldn't either — otherwise commuting alt orderings get
     * spuriously classified `AST_DIVERGED`. See the file header for
     * the full rationale.
     *
     * **Why nothing else gets sorted.** Most other "sibling lists"
     * in Java are order-sensitive in ways that *would* be observable
     * if collapsed:
     *   - Field declarations: initializers can read earlier fields.
     *   - `static {}` initializers: run in source order.
     *   - Inner type declarations: rare to refactor; left positional
     *     to be safe.
     *   - Statements inside a method `Block`: sequential by definition.
     *   - Switch cases: fall-through depends on case order.
     *   - Enum constants: ordinal value is the declaration index.
     * Sorting any of these would let two structurally different
     * programs hash equal. We instead allowlist the single case
     * (methods) that we know is safe AND that we know comes up in
     * practice from the refactorings we replay.
     */
    private fun canonicallyOrderBodyDecls(list: List<*>): List<*> {
        val methods = ArrayList<MethodDeclaration>()
        val rest = ArrayList<Any?>(list.size)
        for (child in list) {
            if (child is MethodDeclaration) methods.add(child) else rest.add(child)
        }
        if (methods.isEmpty()) return list
        methods.sortBy(::methodCanonicalKey)
        return rest + methods
    }

    private fun methodCanonicalKey(m: MethodDeclaration): String {
        val name = m.name.identifier
        val params = m.parameters()
            .filterIsInstance<SingleVariableDeclaration>()
            .joinToString(",") { it.type.toString() }
        return "$name($params)"
    }

    private fun sha256Hex(s: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            if (v < 16) hex.append('0')
            hex.append(v.toString(16))
        }
        return hex.toString()
    }
}
