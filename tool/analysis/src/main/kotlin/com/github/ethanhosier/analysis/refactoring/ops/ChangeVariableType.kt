package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

/**
 * Change the declared type of a local variable / parameter / field
 * to [newTypeFqn]. JDT validates that all usages stay well-typed.
 *
 * Same underlying JDT refactoring serves "Change Variable Type" and
 * "Change Attribute Type" — callers pick the selection point.
 */
data class ChangeVariableTypeRequest(
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
    val newTypeFqn: String,
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
    Integer.TYPE,
    Integer.TYPE,
    String::class.java,
)

fun RefactoringClient.changeVariableType(req: ChangeVariableTypeRequest): RefactoringOutcome =
    invokeOnBundle(
        "changeVariableType",
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
            req.newTypeFqn,
        ),
    )
