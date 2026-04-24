package com.github.ethanhosier.refactoringbundle.internal

import java.nio.file.Path

/**
 * Serialise refactoring results as JSON strings across the OSGi
 * boundary. Hand-rolled so the bundle stays free of kotlinx-
 * serialization — the host parses the result with its own tooling.
 *
 * Format is stable and tiny:
 *   {"status":"ok","changedFiles":["/abs/path1", ...]}
 *   {"status":"failed","reason":"..."}
 */
internal object OutcomeJson {

    fun ok(changedFiles: List<Path>): String {
        val paths = changedFiles.joinToString(",") { "\"${escape(it.toAbsolutePath().toString())}\"" }
        return """{"status":"ok","changedFiles":[$paths]}"""
    }

    fun failed(reason: String): String {
        return """{"status":"failed","reason":"${escape(reason)}"}"""
    }

    private fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        }
    }
}
