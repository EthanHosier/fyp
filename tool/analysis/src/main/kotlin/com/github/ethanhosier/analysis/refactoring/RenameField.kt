package com.github.ethanhosier.analysis.refactoring

import java.nio.file.Path

data class RenameFieldRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val declaringTypeFqn: String,
    val oldName: String,
    val newName: String,
)

private val paramTypes: Array<Class<*>> = arrayOf(
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    String::class.java,
    String::class.java,
    String::class.java,
)

fun RefactoringClient.renameField(req: RenameFieldRequest): RefactoringOutcome =
    invokeOnBundle(
        "renameField",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.declaringTypeFqn,
            req.oldName,
            req.newName,
        ),
    )
