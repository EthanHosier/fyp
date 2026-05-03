package com.github.ethanhosier.analysis.metrics.util

import java.security.MessageDigest

/**
 * Derives a stable identity hash from a [SourceSnippet]-style mini
 * unified-diff: drops file headers and the `@@` hunk header, strips the
 * leading context-marker space from each body line, trims trailing
 * whitespace, joins with `\n`, and hashes with SHA-256.
 *
 * Used to give CPD clone groups a cross-checkpoint identity so trackers
 * can detect when the same body appears or disappears between commits —
 * the body text is the natural invariant of a clone group, since every
 * occurrence in a group has the same body and every group has a unique
 * body within a checkpoint.
 *
 * Returns null when the patch is null/blank or contains no body lines.
 */
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
