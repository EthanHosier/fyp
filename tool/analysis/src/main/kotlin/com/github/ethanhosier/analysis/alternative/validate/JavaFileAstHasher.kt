package com.github.ethanhosier.analysis.alternative.validate

import com.github.ethanhosier.analysis.reconstruct.GitRunner
import com.github.ethanhosier.analysis.refactoring.anchor.AstSubtreeHasher
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.dom.ASTParser
import org.eclipse.jdt.core.dom.CompilationUnit
import java.nio.file.Files
import java.nio.file.Path

object JavaFileAstHasher {

    fun hashFile(worktree: Path, relativePath: String): String? {
        val path = worktree.resolve(relativePath)
        if (!Files.exists(path)) return null
        val source = try {
            Files.readString(path)
        } catch (_: Exception) {
            return null
        }
        return hashSource(source)
    }

    fun hashFileAtSha(git: GitRunner, sha: String, relativePath: String): String? {
        val source = git.showAtSha(sha, relativePath) ?: return null
        return hashSource(source)
    }

    private fun hashSource(source: String): String? {
        val parser = ASTParser.newParser(AST.JLS_Latest).apply {
            setKind(ASTParser.K_COMPILATION_UNIT)
            setSource(source.toCharArray())
            setResolveBindings(false)
        }
        val cu = parser.createAST(null) as? CompilationUnit ?: return null
        return AstSubtreeHasher.hashNode(cu)
    }
}
