package com.github.ethanhosier.analysis.diffs

/**
 * Parses a unified-diff patch string into a structured form. Pure text
 * in → structured records out; no git, no filesystem.
 *
 * Consumed by [PatchFilter] (forthcoming refactor), `PatchLineSurgery`,
 * and `PatchLineRenumber`. Round-tripping a parsed patch back to text
 * (header + body line-by-line) reproduces the input byte-for-byte for
 * well-formed patches.
 */
object UnifiedDiffParser {

    data class ParsedPatch(val files: List<FileEntry>)

    data class FileEntry(
        /** Everything from `diff --git` up to (but not including) the first
         *  `@@` hunk header — i.e. `index`, `---`, `+++`, mode lines, etc. */
        val headerLines: List<String>,
        /** Pre-image path with the `a/` prefix stripped. `null` for files
         *  whose `---` line is `/dev/null` (created files). */
        val oldPath: String?,
        /** Post-image path with the `b/` prefix stripped. `null` for files
         *  whose `+++` line is `/dev/null` (deleted files). */
        val newPath: String?,
        val hunks: List<Hunk>,
    )

    data class Hunk(
        /** Verbatim header line, e.g. `@@ -10,3 +10,5 @@ section heading`. */
        val header: String,
        val oldStart: Int,
        /** Number of pre-image lines covered. Defaults to 1 when the
         *  header omits the count (`@@ -10 +10,5 @@`). */
        val oldLen: Int,
        val newStart: Int,
        val newLen: Int,
        /** Body lines, each still prefixed with ` `, `+`, `-`, or `\`. */
        val body: List<String>,
    )

    fun parse(patch: String): ParsedPatch {
        if (patch.isBlank()) return ParsedPatch(emptyList())
        val lines = patch.split('\n')
        return ParsedPatch(splitFileEntries(lines))
    }

    private fun splitFileEntries(lines: List<String>): List<FileEntry> {
        val entries = mutableListOf<FileEntry>()
        var i = 0
        while (i < lines.size && !lines[i].startsWith("diff --git ")) i++
        while (i < lines.size) {
            val headerStart = i
            i++
            while (i < lines.size &&
                !lines[i].startsWith("@@ ") &&
                !lines[i].startsWith("diff --git ")
            ) i++
            val headerLines = lines.subList(headerStart, i).toList()
            val (oldPath, newPath) = extractPaths(headerLines)

            val hunks = mutableListOf<Hunk>()
            while (i < lines.size && lines[i].startsWith("@@ ")) {
                val header = lines[i]
                val parsed = parseHunkHeader(header)
                i++
                val bodyStart = i
                while (i < lines.size &&
                    !lines[i].startsWith("@@ ") &&
                    !lines[i].startsWith("diff --git ")
                ) i++
                val body = lines.subList(bodyStart, i).toList()
                hunks.add(
                    Hunk(
                        header = header,
                        oldStart = parsed.oldStart,
                        oldLen = parsed.oldLen,
                        newStart = parsed.newStart,
                        newLen = parsed.newLen,
                        body = body,
                    )
                )
            }
            entries.add(FileEntry(headerLines, oldPath, newPath, hunks))
        }
        return entries
    }

    private data class HunkHeaderCounts(
        val oldStart: Int, val oldLen: Int, val newStart: Int, val newLen: Int,
    )

    // `@@ -oldStart[,oldLen] +newStart[,newLen] @@ [section heading]`
    private val hunkHeaderRegex =
        Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""")

    private fun parseHunkHeader(header: String): HunkHeaderCounts {
        val m = hunkHeaderRegex.find(header)
            ?: error("Malformed hunk header: $header")
        val oldStart = m.groupValues[1].toInt()
        val oldLen = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 1
        val newStart = m.groupValues[3].toInt()
        val newLen = m.groupValues[4].takeIf { it.isNotEmpty() }?.toInt() ?: 1
        return HunkHeaderCounts(oldStart, oldLen, newStart, newLen)
    }

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
}
