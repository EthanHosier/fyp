package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

data class MoveClassRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val typeFqn: String,
    val destinationPackage: String,
)

private val paramTypes: Array<Class<*>> = arrayOf(
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    String::class.java,
    String::class.java,
)

fun RefactoringClient.moveClass(req: MoveClassRequest): RefactoringOutcome =
    invokeOnBundle(
        "moveClass",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.typeFqn,
            req.destinationPackage,
        ),
    )
