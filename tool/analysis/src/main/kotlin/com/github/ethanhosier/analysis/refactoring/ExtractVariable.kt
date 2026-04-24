package com.github.ethanhosier.analysis.refactoring

import java.nio.file.Path

data class ExtractVariableRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val relativeFilePath: String,
    val startLine: Int,            // 1-indexed
    val startColumn: Int,          // 1-indexed, inclusive
    val endLine: Int,              // 1-indexed
    val endColumn: Int,            // 1-indexed, inclusive
    val newName: String,
)

private val paramTypes: Array<Class<*>> = arrayOf(
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    String::class.java,
    Int::class.javaPrimitiveType!!,
    Int::class.javaPrimitiveType!!,
    Int::class.javaPrimitiveType!!,
    Int::class.javaPrimitiveType!!,
    String::class.java,
)

fun RefactoringClient.extractVariable(req: ExtractVariableRequest): RefactoringOutcome =
    invokeOnBundle(
        "extractVariable",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.relativeFilePath,
            req.startLine,
            req.startColumn,
            req.endLine,
            req.endColumn,
            req.newName,
        ),
    )
