package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

/**
 * Move an instance field to another class and rename it in a single
 * operation. Host-side composition of [moveInstanceField] +
 * [renameField]. The field keeps its original name during the move,
 * then is renamed on the destination — that order avoids the usual
 * "rename before move" trap where the pre-move rename touches call
 * sites that are about to be rewritten anyway.
 */
data class MoveAndRenameAttributeRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val sourceTypeFqn: String,
    val fieldName: String,
    val destinationTypeFqn: String,
    val newFieldName: String,
)

fun RefactoringClient.moveAndRenameAttribute(req: MoveAndRenameAttributeRequest): RefactoringOutcome {
    val move = moveInstanceField(
        MoveInstanceFieldRequest(
            projectRoot = req.projectRoot,
            sourceFolders = req.sourceFolders,
            classpathJars = req.classpathJars,
            sourceTypeFqn = req.sourceTypeFqn,
            fieldName = req.fieldName,
            destinationTypeFqn = req.destinationTypeFqn,
        ),
    )
    if (move !is RefactoringOutcome.Success) return move

    val rename = renameField(
        RenameFieldRequest(
            projectRoot = req.projectRoot,
            sourceFolders = req.sourceFolders,
            classpathJars = req.classpathJars,
            declaringTypeFqn = req.destinationTypeFqn,
            oldName = req.fieldName,
            newName = req.newFieldName,
        ),
    )
    if (rename !is RefactoringOutcome.Success) return rename

    return RefactoringOutcome.Success((move.changedFiles + rename.changedFiles).distinct())
}
