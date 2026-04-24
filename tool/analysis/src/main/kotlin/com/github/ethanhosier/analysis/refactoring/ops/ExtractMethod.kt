package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome

import java.nio.file.Path

data class ExtractMethodRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val relativeFilePath: String,
    val startLine: Int,            // 1-indexed, inclusive
    val endLine: Int,              // 1-indexed, inclusive
    val newMethodName: String,
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

fun RefactoringClient.extractMethod(req: ExtractMethodRequest): RefactoringOutcome =
    invokeOnBundle(
        "extractMethod",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.relativeFilePath,
            req.startLine,
            req.endLine,
            req.newMethodName,
        ),
    )
