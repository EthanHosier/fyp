package com.github.ethanhosier.analysis.diffs

/**
 * Surgical removal of `+`-runs and `-`-runs from a unified-diff patch.
 *
 * Identifies runs by their *starting line number on the relevant side*:
 *  - `+`-runs by their startLine in the new-tree (post-state).
 *  - `-`-runs by their startLine in the old-tree (pre-state).
 *
 * For each matched run, every body line of the run is dropped from the
 * containing hunk and the hunk header's `-oldStart,oldLen +newStart,newLen`
 * counts are recomputed. If a hunk's body collapses to context-only,
 * the hunk is elided; if a file loses all of its hunks, its entry is
 * dropped from the output.
 *
 * Runs that do not exist in the patch are silently skipped (the caller
 * may legitimately reference runs that an earlier surgery already
 * removed).
 *
 * Pure text in → text out; round-trips a patch with empty surgery
 * specs byte-for-byte modulo the trailing newline.
 */
object PatchLineSurgery {

    private val headerPrefixRegex = Regex("""^@@ -\d+(?:,\d+)? \+\d+(?:,\d+)? @@""")

    fun removeRuns(
        patch: String,
        addedRunStartLines: Map<String, Set<Int>> = emptyMap(),
        removedRunStartLines: Map<String, Set<Int>> = emptyMap(),
    ): String {
        if (patch.isBlank()) return ""
        val parsed = UnifiedDiffParser.parse(patch)
        val out = StringBuilder()

        for (file in parsed.files) {
            val key = file.newPath ?: file.oldPath
            val addedTargets = key?.let { addedRunStartLines[it] }.orEmpty()
            val removedTargets = key?.let { removedRunStartLines[it] }.orEmpty()

            val keptHunks = mutableListOf<UnifiedDiffParser.Hunk>()
            for (hunk in file.hunks) {
                val rewritten = applySurgeryToHunk(hunk, addedTargets, removedTargets)
                if (rewritten != null) keptHunks += rewritten
            }
            if (file.hunks.isNotEmpty() && keptHunks.isEmpty()) continue

            for (h in file.headerLines.dropLastWhile { it.isEmpty() }) out.appendLine(h)
            for (hunk in keptHunks) {
                out.appendLine(hunk.header)
                for (bodyLine in hunk.body.dropLastWhile { it.isEmpty() }) out.appendLine(bodyLine)
            }
        }
        return out.toString()
    }

    private fun applySurgeryToHunk(
        hunk: UnifiedDiffParser.Hunk,
        addedTargets: Set<Int>,
        removedTargets: Set<Int>,
    ): UnifiedDiffParser.Hunk? {
        if (addedTargets.isEmpty() && removedTargets.isEmpty()) return hunk

        val dropIndices = findRunBodyIndices(hunk, addedTargets, removedTargets)
        if (dropIndices.isEmpty()) return hunk

        val keptBody = hunk.body.filterIndexed { i, _ -> i !in dropIndices }
        if (!keptBody.any { it.isNotEmpty() && (it[0] == '+' || it[0] == '-') }) return null

        // Recompute lengths from the kept body.
        var newOldLen = 0
        var newNewLen = 0
        for (raw in keptBody) {
            if (raw.isEmpty()) continue
            when (raw[0]) {
                ' ' -> { newOldLen++; newNewLen++ }
                '-' -> newOldLen++
                '+' -> newNewLen++
                '\\' -> { /* ignored */ }
            }
        }
        // When a modification (oldLen>0, newLen>0) collapses to a pure
        // insertion (oldLen=0) or pure deletion (newLen=0), the unified-
        // diff convention shifts the relevant start by -1: pure-insertion
        // headers reference "the line BEFORE which to insert", and pure-
        // deletion headers reference "the line in the new tree BEFORE
        // which the deletion happens". Without this shift `git apply`
        // rejects the patch with "patch failed: <path>:<line>" because
        // it tries to land the insertion between the wrong adjacent
        // lines.
        val newOldStart = when {
            newOldLen == 0 && hunk.oldLen > 0 -> hunk.oldStart - 1
            else -> hunk.oldStart
        }
        val newNewStart = when {
            newNewLen == 0 && hunk.newLen > 0 -> hunk.newStart - 1
            else -> hunk.newStart
        }

        val match = headerPrefixRegex.find(hunk.header)
            ?: error("Malformed hunk header: ${hunk.header}")
        val suffix = hunk.header.substring(match.range.last + 1)
        val rewrittenHeader = "@@ -$newOldStart,$newOldLen +$newNewStart,$newNewLen @@$suffix"

        return UnifiedDiffParser.Hunk(
            header = rewrittenHeader,
            oldStart = newOldStart,
            oldLen = newOldLen,
            newStart = newNewStart,
            newLen = newNewLen,
            body = keptBody,
        )
    }

    /**
     * Walk the hunk body, identify each contiguous `+`-run / `-`-run with
     * its starting line on the relevant side, and return the set of body
     * indices that belong to runs whose start lines match the targets.
     */
    private fun findRunBodyIndices(
        hunk: UnifiedDiffParser.Hunk,
        addedTargets: Set<Int>,
        removedTargets: Set<Int>,
    ): Set<Int> {
        val drop = mutableSetOf<Int>()

        var oldLine = hunk.oldStart
        var newLine = hunk.newStart

        var curRunSide: Char = ' '
        var curRunStartLine = -1
        var curRunBodyIndices = mutableListOf<Int>()

        fun flush() {
            if (curRunBodyIndices.isNotEmpty()) {
                val matches = when (curRunSide) {
                    '+' -> curRunStartLine in addedTargets
                    '-' -> curRunStartLine in removedTargets
                    else -> false
                }
                if (matches) drop.addAll(curRunBodyIndices)
                curRunBodyIndices = mutableListOf()
                curRunSide = ' '
                curRunStartLine = -1
            }
        }

        for (i in hunk.body.indices) {
            val raw = hunk.body[i]
            if (raw.isEmpty()) continue
            when (raw[0]) {
                ' ' -> {
                    flush(); oldLine++; newLine++
                }
                '+' -> {
                    if (curRunSide != '+') {
                        flush()
                        curRunSide = '+'
                        curRunStartLine = newLine
                    }
                    curRunBodyIndices += i
                    newLine++
                }
                '-' -> {
                    if (curRunSide != '-') {
                        flush()
                        curRunSide = '-'
                        curRunStartLine = oldLine
                    }
                    curRunBodyIndices += i
                    oldLine++
                }
                '\\' -> { /* "\ No newline at end of file" — not a content line */ }
                else -> { /* unknown prefix */ }
            }
        }
        flush()
        return drop
    }
}
