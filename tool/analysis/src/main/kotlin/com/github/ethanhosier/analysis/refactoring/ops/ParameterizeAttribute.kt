package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

/**
 * Promote a field-read expression inside a method to a new parameter
 * of that method. Thin alias for [parameterizeVariable] where the
 * selection happens to cover a field reference — kept as a distinct
 * API because `IdeRelevantRefactorings` lists "Parameterize Variable"
 * and "Parameterize Attribute" as separate entries (RefactoringMiner
 * distinguishes them by what was promoted).
 */
data class ParameterizeAttributeRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val relativeFilePath: String,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val newParameterName: String,
)

fun RefactoringClient.parameterizeAttribute(req: ParameterizeAttributeRequest): RefactoringOutcome =
    parameterizeVariable(
        ParameterizeVariableRequest(
            projectRoot = req.projectRoot,
            sourceFolders = req.sourceFolders,
            classpathJars = req.classpathJars,
            relativeFilePath = req.relativeFilePath,
            startLine = req.startLine,
            startColumn = req.startColumn,
            endLine = req.endLine,
            endColumn = req.endColumn,
            newParameterName = req.newParameterName,
        ),
    )
