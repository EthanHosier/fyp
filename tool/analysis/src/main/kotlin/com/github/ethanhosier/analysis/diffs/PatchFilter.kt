package com.github.ethanhosier.analysis.diffs

/**
 * Filters a unified-diff patch down to only the hunks that touch
 * caller-supplied line ranges. Kept hunks are emitted byte-for-byte
 * (header + body including context) — we never synthesise new diff text,
 * so round-tripping a kept hunk through this function is an identity.
 *
 * A hunk is **kept** iff any *changed* line inside it (a `-` or `+`
 * line — not context) falls within one of:
 *  - [leftRanges]`[oldPath]`: line numbers in the `fromSha` tree, or
 *  - [rightRanges]`[newPath]`: line numbers in the `toSha` tree.
 *
 * A file entry (headers + its hunks) is emitted iff at least one of its
 * hunks was kept. Pure renames with no textual change have no hunks and
 * are therefore dropped — by design, the caller already knows a rename
 * happened from the RefactoringStep itself.
 */
object PatchFilter {

    fun filter(
        patch: String,
        leftRanges: Map<String, List<IntRange>>,
        rightRanges: Map<String, List<IntRange>>,
    ): String {
        if (patch.isBlank()) return ""

        val lines = patch.split('\n')
        val fileEntries = splitFileEntries(lines)
        val out = StringBuilder()

        for (entry in fileEntries) {
            val (oldPath, newPath) = extractPaths(entry.headerLines)
            val leftForFile = oldPath?.let { leftRanges[it] }.orEmpty()
            val rightForFile = newPath?.let { rightRanges[it] }.orEmpty()

            val keptHunks = entry.hunks.filter { hunk ->
                hunkIntersects(hunk, leftForFile, rightForFile)
            }
            if (keptHunks.isEmpty()) continue

            for (h in entry.headerLines) out.appendLine(h)
            for (hunk in keptHunks) {
                out.appendLine(hunk.header)
                for (bodyLine in hunk.body) out.appendLine(bodyLine)
            }
        }

        return out.toString()
    }

    private data class FileEntry(
        val headerLines: List<String>,
        val hunks: List<Hunk>,
    )

    private data class Hunk(
        val header: String,
        val oldStart: Int,
        val newStart: Int,
        val body: List<String>,
    )

    private fun splitFileEntries(lines: List<String>): List<FileEntry> {
        val entries = mutableListOf<FileEntry>()
        var i = 0
        // Skip any pre-amble before the first `diff --git`.
        while (i < lines.size && !lines[i].startsWith("diff --git ")) i++
        while (i < lines.size) {
            // headerLines = everything from `diff --git` up to (but not
            // including) the first `@@` hunk header, or the next
            // `diff --git` (file with no hunks — binary, pure rename).
            val headerStart = i
            i++
            while (i < lines.size &&
                !lines[i].startsWith("@@ ") &&
                !lines[i].startsWith("diff --git ")
            ) i++
            val headerLines = lines.subList(headerStart, i).toList()

            val hunks = mutableListOf<Hunk>()
            while (i < lines.size && lines[i].startsWith("@@ ")) {
                val header = lines[i]
                val (oldStart, newStart) = parseHunkHeader(header)
                i++
                val bodyStart = i
                while (i < lines.size &&
                    !lines[i].startsWith("@@ ") &&
                    !lines[i].startsWith("diff --git ")
                ) i++
                val body = lines.subList(bodyStart, i).toList()
                hunks.add(Hunk(header, oldStart, newStart, body))
            }
            entries.add(FileEntry(headerLines, hunks))
        }
        return entries
    }

    // `@@ -oldStart[,oldLen] +newStart[,newLen] @@ [section heading]`
    private val hunkHeaderRegex = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""")

    private fun parseHunkHeader(header: String): Pair<Int, Int> {
        val m = hunkHeaderRegex.find(header)
            ?: error("Malformed hunk header: $header")
        return m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }

    // Paths come from the `--- a/<path>` / `+++ b/<path>` lines, with
    // `/dev/null` for add/delete. The `a/` and `b/` prefixes are git's
    // default; we strip them. Renames still use the old→new paths here.
    private fun extractPaths(headerLines: List<String>): Pair<String?, String?> {
        var oldPath: String? = null
        var newPath: String? = null
        for (line in headerLines) {
            when {
                line.startsWith("--- ") -> oldPath = stripPathPrefix(line.substring(4))
                line.startsWith("+++ ") -> newPath = stripPathPrefix(line.substring(4))
            }
        }
        return oldPath to newPath
    }

    private fun stripPathPrefix(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed == "/dev/null") return null
        if (trimmed.startsWith("a/") || trimmed.startsWith("b/")) return trimmed.substring(2)
        return trimmed
    }

    private fun hunkIntersects(
        hunk: Hunk,
        leftRanges: List<IntRange>,
        rightRanges: List<IntRange>,
    ): Boolean {
        if (leftRanges.isEmpty() && rightRanges.isEmpty()) return false
        var oldLine = hunk.oldStart
        var newLine = hunk.newStart
        for (line in hunk.body) {
            if (line.isEmpty()) continue
            when (line[0]) {
                ' ' -> { oldLine++; newLine++ }
                '-' -> {
                    if (leftRanges.anyContains(oldLine)) return true
                    oldLine++
                }
                '+' -> {
                    if (rightRanges.anyContains(newLine)) return true
                    newLine++
                }
                '\\' -> { /* "\ No newline at end of file" — not a content line */ }
                else -> { /* Unknown prefix — treat as no-op. */ }
            }
        }
        return false
    }

    private fun List<IntRange>.anyContains(value: Int): Boolean {
        for (r in this) if (value in r) return true
        return false
    }
}
