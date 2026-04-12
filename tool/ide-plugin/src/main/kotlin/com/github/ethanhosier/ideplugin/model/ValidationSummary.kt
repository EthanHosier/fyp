package com.github.ethanhosier.ideplugin.model

import kotlinx.serialization.Serializable

@Serializable
enum class BuildStatus { SUCCESS, FAILURE, UNKNOWN }

@Serializable
enum class TestStatus { PASSED, FAILED, UNKNOWN }

@Serializable
data class ValidationSummary(
    val buildStatus: BuildStatus = BuildStatus.UNKNOWN,
    val testStatus: TestStatus = TestStatus.UNKNOWN,
    val compileErrorCount: Int = 0,
    val scope: String? = null,
    val durationMs: Long? = null,
)
