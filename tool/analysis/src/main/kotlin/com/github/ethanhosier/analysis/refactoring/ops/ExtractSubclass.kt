package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

/**
 * Create a new subclass of [sourceTypeFqn] named [newSubclassName] in
 * the same package, and push the listed members down into it. Bundle
 * side does this as a two-step composition (create-CU + Push Down)
 * because JDT has no single processor for Extract Subclass.
 *
 * Method names must be unambiguous on the source type. Fields must be
 * instance fields present on the source type.
 */
data class ExtractSubclassRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val sourceTypeFqn: String,
    val newSubclassName: String,
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

fun RefactoringClient.extractSubclass(req: ExtractSubclassRequest): RefactoringOutcome =
    invokeOnBundle(
        "extractSubclass",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.sourceTypeFqn,
            req.newSubclassName,
            req.methodNames.toTypedArray(),
            req.fieldNames.toTypedArray(),
        ),
    )
