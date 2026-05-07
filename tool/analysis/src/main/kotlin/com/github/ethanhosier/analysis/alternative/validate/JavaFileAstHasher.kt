package com.github.ethanhosier.analysis.alternative.validate

import com.github.ethanhosier.analysis.refactoring.anchor.AstSubtreeHasher
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.dom.ASTParser
import org.eclipse.jdt.core.dom.CompilationUnit
import java.nio.file.Files
import java.nio.file.Path

/**
 * Canonical AST hash of a single `.java` file.
 *
 * Targeted, per-file hashing — never walks a whole worktree. The
 * validator only ever hashes files that appear in a `git diff`, so
 * a tree-wide snapshotter would be wasted work.
 *
 * Returns `null` when the file is missing or fails to parse. The
 * caller (typically [RefactoringStepValidator]) treats `null` on
 * either side of a comparison as a divergence.
 */
object JavaFileAstHasher {

    fun hashFile(worktree: Path, relativePath: String): String? {
        val path = worktree.resolve(relativePath)
        if (!Files.exists(path)) return null
        val source = try {
            Files.readString(path)
        } catch (_: Exception) {
            return null
        }
        val parser = ASTParser.newParser(AST.JLS_Latest).apply {
            setKind(ASTParser.K_COMPILATION_UNIT)
            setSource(source.toCharArray())
            setResolveBindings(false)
        }
        val cu = parser.createAST(null) as? CompilationUnit ?: return null
        return AstSubtreeHasher.hashNode(cu)
    }
}
