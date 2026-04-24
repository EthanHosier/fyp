package com.github.ethanhosier.analysis.refactoring

import java.nio.file.Path

data class InlineMethodRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val declaringTypeFqn: String,
    val methodName: String,
    // JDT-encoded parameter type signatures; omit when the name is unambiguous.
    val paramTypeSignatures: List<String>? = null,
)

private val paramTypes: Array<Class<*>> = arrayOf(
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    String::class.java,
    String::class.java,
    Array<String>::class.java,
)

fun RefactoringClient.inlineMethod(req: InlineMethodRequest): RefactoringOutcome =
    invokeOnBundle(
        "inlineMethod",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.declaringTypeFqn,
            req.methodName,
            req.paramTypeSignatures?.toTypedArray(),
        ),
    )
