package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

/**
 * Move a class to [destinationPackage] and rename it to [newName] in
 * a single operation. Host-side composition of [moveClass] +
 * [renameClass]; JDT has no single processor for the combined action
 * but IntelliJ surfaces it as one.
 */
data class MoveAndRenameClassRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val typeFqn: String,
    val destinationPackage: String,
    val newName: String,
)

fun RefactoringClient.moveAndRenameClass(req: MoveAndRenameClassRequest): RefactoringOutcome {
    val move = moveClass(
        MoveClassRequest(
            projectRoot = req.projectRoot,
            sourceFolders = req.sourceFolders,
            classpathJars = req.classpathJars,
            typeFqn = req.typeFqn,
            destinationPackage = req.destinationPackage,
        ),
    )
    if (move !is RefactoringOutcome.Success) return move

    val oldSimpleName = req.typeFqn.substringAfterLast('.')
    val rename = renameClass(
        RenameClassRequest(
            projectRoot = req.projectRoot,
            sourceFolders = req.sourceFolders,
            classpathJars = req.classpathJars,
            typeFqn = "${req.destinationPackage}.$oldSimpleName",
            newName = req.newName,
        ),
    )
    if (rename !is RefactoringOutcome.Success) return rename

    return RefactoringOutcome.Success((move.changedFiles + rename.changedFiles).distinct())
}
