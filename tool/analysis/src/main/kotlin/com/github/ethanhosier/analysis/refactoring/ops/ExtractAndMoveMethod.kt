package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

/**
 * Extract a selection into a new method on the enclosing class, then
 * move that method onto the instance named [moveTargetName] (a
 * parameter or field in the extracted method's signature).
 *
 * JDT has no single processor for this — it's a composition of
 * Extract Method + Move Instance Method that IntelliJ surfaces as one
 * action. We chain the two ops; the second call reads on-disk state
 * rewritten by the first, so the declaring type must be passed so we
 * know where to look for the freshly-extracted method.
 */
data class ExtractAndMoveMethodRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val relativeFilePath: String,
    val startLine: Int,
    val endLine: Int,
    val newMethodName: String,
    // FQN of the class containing the extracted selection — needed so
    // the Move Instance Method step can locate the new method.
    val declaringTypeFqn: String,
    val moveTargetName: String,
)

fun RefactoringClient.extractAndMoveMethod(req: ExtractAndMoveMethodRequest): RefactoringOutcome {
    val extract = extractMethod(
        ExtractMethodRequest(
            projectRoot = req.projectRoot,
            sourceFolders = req.sourceFolders,
            classpathJars = req.classpathJars,
            relativeFilePath = req.relativeFilePath,
            startLine = req.startLine,
            endLine = req.endLine,
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
