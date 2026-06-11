package com.github.ethanhosier.analysis.diffs

import com.github.ethanhosier.analysis.alternative.rework.ReworkDriftTracker

object PatchLineRenumber {

    private val headerPrefixRegex = Regex("""^@@ -\d+(?:,\d+)? \+\d+(?:,\d+)? @@""")

    fun renumber(patch: String, tracker: ReworkDriftTracker): String? {
        if (patch.isBlank()) return ""
        val parsed = UnifiedDiffParser.parse(patch)
        val out = StringBuilder()
        for (file in parsed.files) {
            val key = file.newPath ?: file.oldPath

            val rewrittenHunks = mutableListOf<UnifiedDiffParser.Hunk>()
            for (hunk in file.hunks) {
                val rewritten = renumberHunk(hunk, key, tracker) ?: return null
                rewrittenHunks += rewritten
            }
            for (h in file.headerLines.dropLastWhile { it.isEmpty() }) out.appendLine(h)
            for (rh in rewrittenHunks) {
                out.appendLine(rh.header)
                for (bodyLine in rh.body.dropLastWhile { it.isEmpty() }) out.appendLine(bodyLine)
            }
        }
        return out.toString()
    }

    private fun renumberHunk(
        hunk: UnifiedDiffParser.Hunk,
        file: String?,
        tracker: ReworkDriftTracker,
    ): UnifiedDiffParser.Hunk? {
        if (file == null) return hunk

        val newOldStart: Int = if (hunk.oldLen == 0) {
            tracker.translateInsertionPoint(file, hunk.oldStart) ?: return null
        } else {
            tracker.translate(file, hunk.oldStart) ?: return null
        }
        val delta = hunk.newStart - hunk.oldStart
        val newNewStart = newOldStart + delta

        val match = headerPrefixRegex.find(hunk.header)
            ?: error("Malformed hunk header: ${hunk.header}")
        val suffix = hunk.header.substring(match.range.last + 1)
        val newHeader =
            "@@ -$newOldStart,${hunk.oldLen} +$newNewStart,${hunk.newLen} @@$suffix"

        return hunk.copy(
            header = newHeader,
            oldStart = newOldStart,
            newStart = newNewStart,
        )
    }
}
