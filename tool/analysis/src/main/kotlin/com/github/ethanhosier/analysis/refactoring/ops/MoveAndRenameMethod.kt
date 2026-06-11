package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

data class MoveAndRenameMethodRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val sourceTypeFqn: String,
    val methodName: String,
    val targetName: String,
    val targetTypeFqn: String,
    val newMethodName: String,
)

fun RefactoringClient.moveAndRenameMethod(req: MoveAndRenameMethodRequest): RefactoringOutcome {
    val move = moveInstanceMethod(
        MoveInstanceMethodRequest(
            projectRoot = req.projectRoot,
            sourceFolders = req.sourceFolders,
            classpathJars = req.classpathJars,
            sourceTypeFqn = req.sourceTypeFqn,
            methodName = req.methodName,
            targetName = req.targetName,
        ),
    )
    if (move !is RefactoringOutcome.Success) return move

    val rename = renameMethod(
        RenameMethodRequest(
            projectRoot = req.projectRoot,
            sourceFolders = req.sourceFolders,
            classpathJars = req.classpathJars,
            declaringTypeFqn = req.targetTypeFqn,
            oldName = req.methodName,
            newName = req.newMethodName,
        ),
    )
    if (rename !is RefactoringOutcome.Success) return rename

    return RefactoringOutcome.Success((move.changedFiles + rename.changedFiles).distinct())
}
