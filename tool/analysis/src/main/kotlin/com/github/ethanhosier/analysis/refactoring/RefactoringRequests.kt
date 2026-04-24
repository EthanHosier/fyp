package com.github.ethanhosier.analysis.refactoring

import java.nio.file.Path

/**
 * Typed inputs + outputs for [RefactoringClient]. All paths are
 * absolute; [sourceFolders] are relative to [projectRoot] in the
 * worktree's layout (e.g. `"src/main/java"`).
 */
data class ExtractMethodRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val relativeFilePath: String,
    val startLine: Int,            // 1-indexed, inclusive
    val endLine: Int,              // 1-indexed, inclusive
    val newMethodName: String,
)

data class RenameMethodRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val declaringTypeFqn: String,
    val oldName: String,
    val newName: String,
    // JDT-encoded parameter type signatures ("Ljava/lang/String;", "I",
    // "V", …). Omit to let the client auto-pick when the name is
    // unambiguous; include to disambiguate overloads.
    val paramTypeSignatures: List<String>? = null,
)

sealed interface RefactoringOutcome {
    data class Success(val changedFiles: List<Path>) : RefactoringOutcome
    data class Failed(val reason: String) : RefactoringOutcome
}
