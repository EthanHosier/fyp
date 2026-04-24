package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

/**
 * Promote an expression to a `static final` field. [visibility] is
 * one of `"public"`, `"protected"`, `"private"`, or `""` for
 * package-private.
 */
data class ExtractAttributeRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val relativeFilePath: String,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val newName: String,
    val visibility: String = "private",
)

private val paramTypes: Array<Class<*>> = arrayOf(
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    String::class.java,
    Integer.TYPE,
    Integer.TYPE,
    Integer.TYPE,
    Integer.TYPE,
    String::class.java,
    String::class.java,
)

fun RefactoringClient.extractAttribute(req: ExtractAttributeRequest): RefactoringOutcome =
    invokeOnBundle(
        "extractAttribute",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.relativeFilePath,
            req.startLine,
            req.startColumn,
            req.endLine,
            req.endColumn,
            req.newName,
            req.visibility,
        ),
    )
