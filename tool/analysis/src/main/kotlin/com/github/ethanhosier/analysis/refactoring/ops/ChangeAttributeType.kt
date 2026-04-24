package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

/**
 * Change the declared type of a field identified by FQN + name to
 * [newTypeFqn]. Convenience wrapper over JDT's Change Type
 * refactoring — caller doesn't need to compute file offsets, we
 * locate the declaration via the Java model.
 */
data class ChangeAttributeTypeRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val declaringTypeFqn: String,
    val fieldName: String,
    val newTypeFqn: String,
)

private val paramTypes: Array<Class<*>> = arrayOf(
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    String::class.java,
    String::class.java,
    String::class.java,
)

fun RefactoringClient.changeAttributeType(req: ChangeAttributeTypeRequest): RefactoringOutcome =
    invokeOnBundle(
        "changeAttributeType",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.declaringTypeFqn,
            req.fieldName,
            req.newTypeFqn,
        ),
    )
