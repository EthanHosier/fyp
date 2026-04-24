package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

/**
 * Change the declared type of a local variable / parameter / field
 * whose name is at ([line], [column]) (1-indexed) in
 * [relativeFilePath] to [newTypeFqn]. JDT validates that all usages
 * stay well-typed; [newTypeFqn] must be a supertype or otherwise
 * compatible.
 *
 * Same underlying JDT refactoring serves "Change Variable Type" and
 * "Change Attribute Type" — callers pick the selection point.
 */
data class ChangeVariableTypeRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val relativeFilePath: String,
    val line: Int,
    val column: Int,
    val newTypeFqn: String,
)

private val paramTypes: Array<Class<*>> = arrayOf(
    String::class.java,
    Array<String>::class.java,
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
            req.line,
            req.column,
            req.newTypeFqn,
        ),
    )
