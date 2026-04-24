package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

data class ExtractSuperclassRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val sourceTypeFqn: String,
    val newSupertypeName: String,
    val methodNames: List<String> = emptyList(),
    val fieldNames: List<String> = emptyList(),
)

private val paramTypes: Array<Class<*>> = arrayOf(
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    String::class.java,
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
)

fun RefactoringClient.extractSuperclass(req: ExtractSuperclassRequest): RefactoringOutcome =
    invokeOnBundle(
        "extractSuperclass",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.sourceTypeFqn,
            req.newSupertypeName,
            req.methodNames.toTypedArray(),
            req.fieldNames.toTypedArray(),
        ),
    )
