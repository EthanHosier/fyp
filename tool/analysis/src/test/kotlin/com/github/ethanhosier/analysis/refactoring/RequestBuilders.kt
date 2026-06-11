package com.github.ethanhosier.analysis.refactoring

import com.github.ethanhosier.analysis.refactoring.anchor.SpecAnchorBuilder
import com.github.ethanhosier.analysis.refactoring.ops.ChangeVariableTypeRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractAttributeRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractMethodRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractVariableRequest
import com.github.ethanhosier.analysis.refactoring.ops.InlineVariableRequest
import com.github.ethanhosier.analysis.refactoring.ops.ParameterizeAttributeRequest
import com.github.ethanhosier.analysis.refactoring.ops.ParameterizeVariableRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenameLocalVariableRequest
import com.github.ethanhosier.analysis.refactoring.ops.ReplaceVariableWithAttributeRequest
import java.nio.file.Path


private val UNRESOLVABLE_RANGE = SpecAnchorBuilder.RangeAnchor(
    declaringTypeFqn = "<unresolved>",
    hostMethodName = "<unresolved>",
    hostMethodParamTypes = emptyList(),
    selectionSubtreeHash = "",
    selectionNodeCount = 0,
)
private val UNRESOLVABLE_POINT = SpecAnchorBuilder.PointAnchor(
    declaringTypeFqn = "<unresolved>",
    hostMethodName = "<unresolved>",
    hostMethodParamTypes = emptyList(),
    declarationSubtreeHash = "",
)

private fun rangeAnchor(
    projectRoot: Path,
    relativeFilePath: String,
    startLine: Int, startColumn: Int, endLine: Int, endColumn: Int,
): SpecAnchorBuilder.RangeAnchor =
    SpecAnchorBuilder(projectRoot).rangeAnchor(relativeFilePath, startLine, startColumn, endLine, endColumn)
        ?: UNRESOLVABLE_RANGE

private fun pointAnchor(
    projectRoot: Path,
    relativeFilePath: String,
    line: Int, column: Int,
): SpecAnchorBuilder.PointAnchor =
    SpecAnchorBuilder(projectRoot).pointAnchor(relativeFilePath, line, column)
        ?: UNRESOLVABLE_POINT

@Suppress("LongParameterList")
fun extractMethodRequestAt(
    projectRoot: Path,
    sourceFolders: List<String>,
    classpathJars: List<Path>,
    relativeFilePath: String,
    startLine: Int, startColumn: Int, endLine: Int, endColumn: Int,
    newMethodName: String,
    isStatic: Boolean = false,
): ExtractMethodRequest {
    val a = rangeAnchor(projectRoot, relativeFilePath, startLine, startColumn, endLine, endColumn)
    return ExtractMethodRequest(
        projectRoot = projectRoot,
        sourceFolders = sourceFolders,
        classpathJars = classpathJars,
        relativeFilePath = relativeFilePath,
        declaringTypeFqn = a.declaringTypeFqn,
        hostMethodName = a.hostMethodName,
        hostMethodParamTypes = a.hostMethodParamTypes,
        selectionSubtreeHash = a.selectionSubtreeHash,
        selectionNodeCount = a.selectionNodeCount,
        originalLineHint = startLine,
        originalColumnHint = startColumn,
        newMethodName = newMethodName,
        isStatic = isStatic,
    )
}

@Suppress("LongParameterList")
fun extractVariableRequestAt(
    projectRoot: Path,
    sourceFolders: List<String>,
    classpathJars: List<Path>,
    relativeFilePath: String,
    startLine: Int, startColumn: Int, endLine: Int, endColumn: Int,
    newName: String,
): ExtractVariableRequest {
    val a = rangeAnchor(projectRoot, relativeFilePath, startLine, startColumn, endLine, endColumn)
    return ExtractVariableRequest(
        projectRoot = projectRoot,
        sourceFolders = sourceFolders,
        classpathJars = classpathJars,
        relativeFilePath = relativeFilePath,
        declaringTypeFqn = a.declaringTypeFqn,
        hostMethodName = a.hostMethodName,
        hostMethodParamTypes = a.hostMethodParamTypes,
        selectionSubtreeHash = a.selectionSubtreeHash,
        selectionNodeCount = a.selectionNodeCount,
        originalLineHint = startLine,
        originalColumnHint = startColumn,
        newName = newName,
    )
}

@Suppress("LongParameterList")
fun extractAttributeRequestAt(
    projectRoot: Path,
    sourceFolders: List<String>,
    classpathJars: List<Path>,
    relativeFilePath: String,
    startLine: Int, startColumn: Int, endLine: Int, endColumn: Int,
    newName: String,
    visibility: String = "private",
): ExtractAttributeRequest {
    val a = rangeAnchor(projectRoot, relativeFilePath, startLine, startColumn, endLine, endColumn)
    return ExtractAttributeRequest(
        projectRoot = projectRoot,
        sourceFolders = sourceFolders,
        classpathJars = classpathJars,
        relativeFilePath = relativeFilePath,
        declaringTypeFqn = a.declaringTypeFqn,
        hostMethodName = a.hostMethodName,
        hostMethodParamTypes = a.hostMethodParamTypes,
        selectionSubtreeHash = a.selectionSubtreeHash,
        selectionNodeCount = a.selectionNodeCount,
        originalLineHint = startLine,
        originalColumnHint = startColumn,
        newName = newName,
        visibility = visibility,
    )
}

@Suppress("LongParameterList")
fun parameterizeVariableRequestAt(
    projectRoot: Path,
    sourceFolders: List<String>,
    classpathJars: List<Path>,
    relativeFilePath: String,
    startLine: Int, startColumn: Int, endLine: Int, endColumn: Int,
    newParameterName: String,
): ParameterizeVariableRequest {
    val a = rangeAnchor(projectRoot, relativeFilePath, startLine, startColumn, endLine, endColumn)
    return ParameterizeVariableRequest(
        projectRoot = projectRoot,
        sourceFolders = sourceFolders,
        classpathJars = classpathJars,
        relativeFilePath = relativeFilePath,
        declaringTypeFqn = a.declaringTypeFqn,
        hostMethodName = a.hostMethodName,
        hostMethodParamTypes = a.hostMethodParamTypes,
        selectionSubtreeHash = a.selectionSubtreeHash,
        selectionNodeCount = a.selectionNodeCount,
        originalLineHint = startLine,
        originalColumnHint = startColumn,
        newParameterName = newParameterName,
    )
}

@Suppress("LongParameterList")
fun parameterizeAttributeRequestAt(
    projectRoot: Path,
    sourceFolders: List<String>,
    classpathJars: List<Path>,
    relativeFilePath: String,
    startLine: Int, startColumn: Int, endLine: Int, endColumn: Int,
    newParameterName: String,
): ParameterizeAttributeRequest {
    val a = rangeAnchor(projectRoot, relativeFilePath, startLine, startColumn, endLine, endColumn)
    return ParameterizeAttributeRequest(
        projectRoot = projectRoot,
        sourceFolders = sourceFolders,
        classpathJars = classpathJars,
        relativeFilePath = relativeFilePath,
        declaringTypeFqn = a.declaringTypeFqn,
        hostMethodName = a.hostMethodName,
        hostMethodParamTypes = a.hostMethodParamTypes,
        selectionSubtreeHash = a.selectionSubtreeHash,
        selectionNodeCount = a.selectionNodeCount,
        originalLineHint = startLine,
        originalColumnHint = startColumn,
        newParameterName = newParameterName,
    )
}

fun inlineVariableRequestAt(
    projectRoot: Path,
    sourceFolders: List<String>,
    classpathJars: List<Path>,
    relativeFilePath: String,
    line: Int, column: Int,
): InlineVariableRequest {
    val a = pointAnchor(projectRoot, relativeFilePath, line, column)
    return InlineVariableRequest(
        projectRoot = projectRoot,
        sourceFolders = sourceFolders,
        classpathJars = classpathJars,
        relativeFilePath = relativeFilePath,
        declaringTypeFqn = a.declaringTypeFqn,
        hostMethodName = a.hostMethodName,
        hostMethodParamTypes = a.hostMethodParamTypes,
        declarationSubtreeHash = a.declarationSubtreeHash,
        originalLineHint = line,
        originalColumnHint = column,
    )
}

@Suppress("LongParameterList")
fun renameLocalVariableRequestAt(
    projectRoot: Path,
    sourceFolders: List<String>,
    classpathJars: List<Path>,
    relativeFilePath: String,
    line: Int, column: Int,
    newName: String,
): RenameLocalVariableRequest {
    val a = pointAnchor(projectRoot, relativeFilePath, line, column)
    return RenameLocalVariableRequest(
        projectRoot = projectRoot,
        sourceFolders = sourceFolders,
        classpathJars = classpathJars,
        relativeFilePath = relativeFilePath,
        declaringTypeFqn = a.declaringTypeFqn,
        hostMethodName = a.hostMethodName,
        hostMethodParamTypes = a.hostMethodParamTypes,
        declarationSubtreeHash = a.declarationSubtreeHash,
        originalLineHint = line,
        originalColumnHint = column,
        newName = newName,
    )
}

@Suppress("LongParameterList")
fun changeVariableTypeRequestAt(
    projectRoot: Path,
    sourceFolders: List<String>,
    classpathJars: List<Path>,
    relativeFilePath: String,
    line: Int, column: Int,
    newTypeFqn: String,
): ChangeVariableTypeRequest {
    val a = pointAnchor(projectRoot, relativeFilePath, line, column)
    return ChangeVariableTypeRequest(
        projectRoot = projectRoot,
        sourceFolders = sourceFolders,
        classpathJars = classpathJars,
        relativeFilePath = relativeFilePath,
        declaringTypeFqn = a.declaringTypeFqn,
        hostMethodName = a.hostMethodName,
        hostMethodParamTypes = a.hostMethodParamTypes,
        declarationSubtreeHash = a.declarationSubtreeHash,
        originalLineHint = line,
        originalColumnHint = column,
        newTypeFqn = newTypeFqn,
    )
}

@Suppress("LongParameterList")
fun replaceVariableWithAttributeRequestAt(
    projectRoot: Path,
    sourceFolders: List<String>,
    classpathJars: List<Path>,
    relativeFilePath: String,
    line: Int, column: Int,
    newFieldName: String,
    visibility: String = "private",
): ReplaceVariableWithAttributeRequest {
    val a = pointAnchor(projectRoot, relativeFilePath, line, column)
    return ReplaceVariableWithAttributeRequest(
        projectRoot = projectRoot,
        sourceFolders = sourceFolders,
        classpathJars = classpathJars,
        relativeFilePath = relativeFilePath,
        declaringTypeFqn = a.declaringTypeFqn,
        hostMethodName = a.hostMethodName,
        hostMethodParamTypes = a.hostMethodParamTypes,
        declarationSubtreeHash = a.declarationSubtreeHash,
        originalLineHint = line,
        originalColumnHint = column,
        newFieldName = newFieldName,
        visibility = visibility,
    )
}
