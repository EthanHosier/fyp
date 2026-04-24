package com.github.ethanhosier.analysis.refactoring

import java.nio.file.Path

data class RenameClassRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val typeFqn: String,
    val newName: String,
)

private val paramTypes: Array<Class<*>> = arrayOf(
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    String::class.java,
    String::class.java,
)

fun RefactoringClient.renameClass(req: RenameClassRequest): RefactoringOutcome =
    invokeOnBundle(
        "renameClass",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.typeFqn,
            req.newName,
        ),
    )
