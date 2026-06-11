package com.github.ethanhosier.refactoringbundle.internal

import java.nio.file.Path

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
