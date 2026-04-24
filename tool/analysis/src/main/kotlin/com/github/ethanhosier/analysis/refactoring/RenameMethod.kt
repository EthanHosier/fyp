package com.github.ethanhosier.analysis.refactoring

import java.nio.file.Path

data class RenameMethodRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val declaringTypeFqn: String,
    val oldName: String,
    val newName: String,
    // JDT-encoded parameter type signatures ("Ljava/lang/String;", "I",
    // "V", …). Omit to let the client auto-pick when the name is
    // unambiguous; include to disambiguate overloads.
    val paramTypeSignatures: List<String>? = null,
)

private val paramTypes: Array<Class<*>> = arrayOf(
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    String::class.java,
    String::class.java,
    String::class.java,
    Array<String>::class.java,
)

fun RefactoringClient.renameMethod(req: RenameMethodRequest): RefactoringOutcome =
    invokeOnBundle(
        "renameMethod",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.declaringTypeFqn,
            req.oldName,
            req.newName,
            req.paramTypeSignatures?.toTypedArray(),
        ),
    )
