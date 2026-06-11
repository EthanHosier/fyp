package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

data class MoveInstanceMethodRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val sourceTypeFqn: String,
    val methodName: String,          // must be unambiguous on sourceTypeFqn
    val targetName: String,
)

private val paramTypes: Array<Class<*>> = arrayOf(
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    String::class.java,
    String::class.java,
    String::class.java,
)

fun RefactoringClient.moveInstanceMethod(req: MoveInstanceMethodRequest): RefactoringOutcome =
    invokeOnBundle(
        "moveInstanceMethod",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.sourceTypeFqn,
            req.methodName,
            req.targetName,
        ),
    )
