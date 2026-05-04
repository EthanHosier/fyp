package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

/**
 * Promote an expression inside a method body into a new parameter of
 * the enclosing method. All existing call sites pass the original
 * expression.
 *
 * Same underlying JDT refactoring serves "Parameterize Variable" (for
 * a local) and "Parameterize Attribute" (for a field read) — caller
 * picks the selection; see [parameterizeAttribute] for a
 * declaration-oriented wrapper.
 */
data class ParameterizeVariableRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val relativeFilePath: String,
    val declaringTypeFqn: String,
    val hostMethodName: String,
    val hostMethodParamTypes: List<String>,
    val selectionSubtreeHash: String,
    val selectionNodeCount: Int,
    val originalLineHint: Int? = null,
    val originalColumnHint: Int? = null,
    val newParameterName: String,
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
    Integer.TYPE,
    String::class.java,
)

fun RefactoringClient.parameterizeVariable(req: ParameterizeVariableRequest): RefactoringOutcome =
    invokeOnBundle(
        "parameterizeVariable",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.relativeFilePath,
            req.declaringTypeFqn,
            req.hostMethodName,
            req.hostMethodParamTypes.toTypedArray(),
            req.selectionSubtreeHash,
            req.selectionNodeCount,
            req.originalLineHint ?: -1,
            req.originalColumnHint ?: -1,
            req.newParameterName,
        ),
    )
