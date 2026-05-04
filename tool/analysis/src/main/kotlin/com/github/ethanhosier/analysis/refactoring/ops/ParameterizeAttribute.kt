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
    val declaringTypeFqn: String,
    val hostMethodName: String,
    val hostMethodParamTypes: List<String>,
    val selectionSubtreeHash: String,
    val selectionNodeCount: Int,
    val originalLineHint: Int? = null,
    val originalColumnHint: Int? = null,
    val newParameterName: String,
)

fun RefactoringClient.parameterizeAttribute(req: ParameterizeAttributeRequest): RefactoringOutcome =
    parameterizeVariable(
        ParameterizeVariableRequest(
            projectRoot = req.projectRoot,
            sourceFolders = req.sourceFolders,
            classpathJars = req.classpathJars,
            relativeFilePath = req.relativeFilePath,
            declaringTypeFqn = req.declaringTypeFqn,
            hostMethodName = req.hostMethodName,
            hostMethodParamTypes = req.hostMethodParamTypes,
            selectionSubtreeHash = req.selectionSubtreeHash,
            selectionNodeCount = req.selectionNodeCount,
            originalLineHint = req.originalLineHint,
            originalColumnHint = req.originalColumnHint,
            newParameterName = req.newParameterName,
        ),
    )
