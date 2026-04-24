package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome

import java.nio.file.Path

data class RenameLocalVariableRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val relativeFilePath: String,
    val line: Int,                 // 1-indexed
    val column: Int,               // 1-indexed; any position within the variable's name
    val newName: String,
)

private val paramTypes: Array<Class<*>> = arrayOf(
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    String::class.java,
    Int::class.javaPrimitiveType!!,
    Int::class.javaPrimitiveType!!,
    String::class.java,
)

fun RefactoringClient.renameLocalVariable(req: RenameLocalVariableRequest): RefactoringOutcome =
    invokeOnBundle(
        "renameLocalVariable",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.relativeFilePath,
            req.line,
            req.column,
            req.newName,
        ),
    )
