package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

data class PullUpRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val declaringTypeFqn: String,
    // Method names unambiguous on [declaringTypeFqn]. If you need
    // overload disambiguation, pull up one method per call.
    val methodNames: List<String> = emptyList(),
    val fieldNames: List<String> = emptyList(),
)

private val paramTypes: Array<Class<*>> = arrayOf(
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
)

fun RefactoringClient.pullUp(req: PullUpRequest): RefactoringOutcome =
    invokeOnBundle(
        "pullUp",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.declaringTypeFqn,
            req.methodNames.toTypedArray(),
            req.fieldNames.toTypedArray(),
        ),
    )
