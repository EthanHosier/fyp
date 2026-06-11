package com.github.ethanhosier.analysis.miner.model

import kotlinx.serialization.Serializable

@Serializable
data class DetectedRefactoring(
    val type: String,
    val description: String,
    val leftSideLocations: List<RefactoringLocation>,
    val rightSideLocations: List<RefactoringLocation>,
    val ideRelevant: Boolean,
)

@Serializable
data class RefactoringLocation(
    val key: String,
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val startColumn: Int,
    val endColumn: Int,
    val codeElementType: String,
    val codeElement: String?,
)
