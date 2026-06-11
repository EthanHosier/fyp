package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

data class MovePackageRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val oldPackage: String,
    val newParentPackage: String,
)

fun RefactoringClient.movePackage(req: MovePackageRequest): RefactoringOutcome {
    val lastSegment = req.oldPackage.substringAfterLast('.')
    val newPackage = if (req.newParentPackage.isEmpty()) lastSegment else "${req.newParentPackage}.$lastSegment"
    return renamePackage(
        RenamePackageRequest(
            projectRoot = req.projectRoot,
            sourceFolders = req.sourceFolders,
            classpathJars = req.classpathJars,
            oldPackage = req.oldPackage,
            newPackage = newPackage,
        ),
    )
}
