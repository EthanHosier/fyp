package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

/**
 * Promote a local variable to a field on the enclosing class.
 * [visibility] is one of `"public"`, `"protected"`, `"private"`, or
 * `""` for package-private. The initializer stays at the original
 * declaration site by default.
 */
data class ReplaceVariableWithAttributeRequest(
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
    val newFieldName: String,
    val visibility: String = "private",
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
    String::class.java,
)

fun RefactoringClient.replaceVariableWithAttribute(req: ReplaceVariableWithAttributeRequest): RefactoringOutcome =
    invokeOnBundle(
        "replaceVariableWithAttribute",
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
            req.newFieldName,
            req.visibility,
        ),
    )
