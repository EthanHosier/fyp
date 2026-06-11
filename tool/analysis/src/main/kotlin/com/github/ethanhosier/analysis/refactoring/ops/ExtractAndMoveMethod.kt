package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import com.github.ethanhosier.analysis.refactoring.anchor.SpecAnchorBuilder
import java.nio.file.Path

data class ExtractAndMoveMethodRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val relativeFilePath: String,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val newMethodName: String,
    // FQN of the class containing the extracted selection — needed so
    // the Move Instance Method step can locate the new method.
    val declaringTypeFqn: String,
    val moveTargetName: String,
)

fun RefactoringClient.extractAndMoveMethod(req: ExtractAndMoveMethodRequest): RefactoringOutcome {
    val anchor = SpecAnchorBuilder(req.projectRoot).rangeAnchor(
        req.relativeFilePath, req.startLine, req.startColumn, req.endLine, req.endColumn,
    ) ?: return RefactoringOutcome.Failed(
        "could not resolve AST anchor for ${req.relativeFilePath}:${req.startLine}:${req.startColumn}",
    )

    val extract = extractMethod(
        ExtractMethodRequest(
            projectRoot = req.projectRoot,
            sourceFolders = req.sourceFolders,
            classpathJars = req.classpathJars,
            relativeFilePath = req.relativeFilePath,
            declaringTypeFqn = anchor.declaringTypeFqn,
            hostMethodName = anchor.hostMethodName,
            hostMethodParamTypes = anchor.hostMethodParamTypes,
            selectionSubtreeHash = anchor.selectionSubtreeHash,
            selectionNodeCount = anchor.selectionNodeCount,
            originalLineHint = req.startLine,
            originalColumnHint = req.startColumn,
            newMethodName = req.newMethodName,
        ),
    )
    if (extract !is RefactoringOutcome.Success) return extract

    val move = moveInstanceMethod(
        MoveInstanceMethodRequest(
            projectRoot = req.projectRoot,
            sourceFolders = req.sourceFolders,
            classpathJars = req.classpathJars,
            sourceTypeFqn = req.declaringTypeFqn,
            methodName = req.newMethodName,
            targetName = req.moveTargetName,
        ),
    )
    if (move !is RefactoringOutcome.Success) return move

    return RefactoringOutcome.Success((extract.changedFiles + move.changedFiles).distinct())
}
