package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

data class ExtractClassRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val sourceTypeFqn: String,
    val newClassName: String,
    val delegateFieldName: String,
    // Instance fields on [sourceTypeFqn] to move into the extracted
    // class. Order is preserved in the generated class.
    val fieldNames: List<String>,
    val createGetterSetter: Boolean = false,
)

private val paramTypes: Array<Class<*>> = arrayOf(
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    String::class.java,
    String::class.java,
    String::class.java,
    Array<String>::class.java,
    java.lang.Boolean.TYPE,
)

fun RefactoringClient.extractClass(req: ExtractClassRequest): RefactoringOutcome =
    invokeOnBundle(
        "extractClass",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.sourceTypeFqn,
            req.newClassName,
            req.delegateFieldName,
            req.fieldNames.toTypedArray(),
            req.createGetterSetter,
        ),
    )
