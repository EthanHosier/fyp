package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome

import java.nio.file.Path

data class InlineVariableRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val relativeFilePath: String,
    val declaringTypeFqn: String,
    val hostMethodName: String,
    val hostMethodParamTypes: List<String>,
    val declarationSubtreeHash: String,
    val originalLineHint: Int? = null,
    val originalColumnHint: Int? = null,
)

private val paramTypes: Array<Class<*>> = arrayOf(
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    String::class.java,
    String::class.java,
    String::class.java,
    Array<String>::class.java,
    String::class.java,
    Int::class.javaPrimitiveType!!,
    Int::class.javaPrimitiveType!!,
)

fun RefactoringClient.inlineVariable(req: InlineVariableRequest): RefactoringOutcome =
    invokeOnBundle(
        "inlineVariable",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.relativeFilePath,
            req.declaringTypeFqn,
            req.hostMethodName,
            req.hostMethodParamTypes.toTypedArray(),
            req.declarationSubtreeHash,
            req.originalLineHint ?: -1,
            req.originalColumnHint ?: -1,
        ),
    )
