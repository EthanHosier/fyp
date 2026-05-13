package com.github.ethanhosier.analysis.diffs

import com.github.ethanhosier.analysis.alternative.rework.ReworkDriftTracker

/**
 * Translates a unified-diff patch's hunk headers from user-tree
 * coords into synthetic-tree coords using a [ReworkDriftTracker].
 * The hunk body — context, `+` and `-` lines — is preserved
 * byte-for-byte; only the `-oldStart,oldLen +newStart,newLen`
 * header numbers change.
 *
 * Translation rules per hunk:
 *  - **Modification** (`oldLen > 0`): `oldStart` is translated
 *    directly via [ReworkDriftTracker.translate]. If it falls inside
 *    an ADDED-zone interior, the hunk is untranslatable and
 *    [renumber] returns `null` for the whole patch (the caller
 *    skips this step and continues replay).
 *  - **Pure insertion** (`oldLen == 0`): the hunk's `oldStart` is
 *    a boundary, not a line, so [ReworkDriftTracker.translateInsertionPoint]
 *    is used (with the "scan past zone" rule to land insertions at
 *    the end of an ADDED zone at the correct synth boundary).
 *
 * `newStart` translates by preserving its offset from `oldStart` —
 * i.e. `newNewStart = newOldStart + (newStart - oldStart)`. This
 * captures the cumulative net-line delta from prior hunks within
 * the same patch.
 *
 * Pure text → text → text-or-null; no git, no filesystem.
 */
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
