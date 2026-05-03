package com.github.ethanhosier.analysis.metrics.util

import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads a line range from a worktree file and wraps it as a self-contained
 * mini unified-diff string: one file header, one hunk with every body line
 * marked as context (` ` prefix), absolute line numbers in the `@@` header.
 *
 * This is *not* a real diff — the format is reused so the dashboard's
 * `@pierre/diffs` renderer can display the snippet with Shiki syntax
 * highlighting and a correctly numbered gutter. Context width matches
 * [com.github.ethanhosier.analysis.reconstruct.GitRunner.diffPatch]'s default
 * so different snippet sources align visually.
 *
 * Returns null when the file is missing/unreadable or the requested range is
 * outside the file — caller should treat that as "no snippet available", not
 * an error worth surfacing.
 */
object SourceSnippet {
    /** Lines of surrounding source kept either side of the requested range. */
    const val CONTEXT_LINES: Int = 3

    fun load(
        root: Path,
        relPath: String,
        beginLine: Int,
        endLine: Int,
    ): String? = runCatching {
        val lines = Files.readAllLines(root.resolve(relPath), Charsets.UTF_8)
        val from = (beginLine - CONTEXT_LINES - 1).coerceAtLeast(0)
        val to = (endLine + CONTEXT_LINES).coerceAtMost(lines.size)
        if (from >= to) return@runCatching null
        val startLine = from + 1
        val count = to - from
        val body = lines.subList(from, to).joinToString("\n") { " $it" }
        // Trailing newline matches `git diff` output and keeps the
        // dashboard's parser from warning about an unterminated last line.
        buildString {
            append("diff --git a/").append(relPath).append(" b/").append(relPath).append('\n')
            append("--- a/").append(relPath).append('\n')
            append("+++ b/").append(relPath).append('\n')
            append("@@ -").append(startLine).append(',').append(count)
                .append(" +").append(startLine).append(',').append(count).append(" @@\n")
            append(body).append('\n')
        }
    }.getOrNull()
}
