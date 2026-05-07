package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome

import java.nio.file.Path

data class ExtractMethodRequest(
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
    val newMethodName: String,
    val isStatic: Boolean = false,
)

private val paramTypes: Array<Class<*>> = arrayOf(
    String::class.java,                     // projectRoot
    Array<String>::class.java,              // sourceFolders
    Array<String>::class.java,              // classpathJars
    String::class.java,                     // relativeFilePath
    String::class.java,                     // declaringTypeFqn
    String::class.java,                     // hostMethodName
    Array<String>::class.java,              // hostMethodParamTypes
    String::class.java,                     // extractedSubtreeHash
    Int::class.javaPrimitiveType!!,         // extractedStatementCount
    Int::class.javaPrimitiveType!!,         // originalStartLineHint (-1 = absent)
    Int::class.javaPrimitiveType!!,         // originalStartColumnHint
    String::class.java,                     // newMethodName
    Boolean::class.javaPrimitiveType!!,     // isStatic
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
            req.declaringTypeFqn,
            req.hostMethodName,
            req.hostMethodParamTypes.toTypedArray(),
            req.selectionSubtreeHash,
            req.selectionNodeCount,
            req.originalLineHint ?: -1,
            req.originalColumnHint ?: -1,
            req.newMethodName,
            req.isStatic,
        ),
    )
