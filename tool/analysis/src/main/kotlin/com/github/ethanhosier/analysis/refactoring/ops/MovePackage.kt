package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

/**
 * Move a package to be a child of [newParentPackage], keeping its
 * last name segment. Differs from [renamePackage] in intent only —
 * Rename Package changes the last segment, Move Package changes the
 * prefix and keeps the last segment. JDT uses the same descriptor for
 * both; we compose on top of [renamePackage] to preserve that.
 *
 * Example: moving `com.example.old` under `com.foo` yields
 * `com.foo.old`.
 */
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
