package com.github.ethanhosier.analysis.metrics.util

import java.security.MessageDigest

object SnippetIdentity {
    fun fromPatch(patch: String?): String? {
        if (patch.isNullOrBlank()) return null
        val lines = patch.lineSequence().toList()
        val hunkIdx = lines.indexOfFirst { it.startsWith("@@") }
        if (hunkIdx < 0) return null
        val body = lines.subList(hunkIdx + 1, lines.size)
            .takeWhile { it.startsWith(" ") || it.isEmpty() }
            .filter { it.isNotEmpty() }
            .map { it.removePrefix(" ").trimEnd() }
        if (body.isEmpty()) return null
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(body.joinToString("\n").toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
