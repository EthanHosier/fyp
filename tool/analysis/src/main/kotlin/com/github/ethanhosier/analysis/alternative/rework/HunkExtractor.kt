package com.github.ethanhosier.analysis.alternative.rework

import com.github.ethanhosier.analysis.diffs.UnifiedDiffParser

object HunkExtractor {

    data class FileHunks(
        val oldPath: String?,
        val newPath: String?,
        val hunks: List<HunkRuns>,
    )

    data class HunkRuns(
        val header: String,
        val addedRuns: List<LineRun>,
        val removedRuns: List<LineRun>,
    )

    data class LineRun(
        val startLine: Int,
        val lines: List<String>,
    )

    fun extractFromPatch(patch: String): List<FileHunks> =
        UnifiedDiffParser.parse(patch).files.map { fe ->
            FileHunks(
                oldPath = fe.oldPath,
                newPath = fe.newPath,
                hunks = fe.hunks.map { partitionRuns(it) },
            )
        }

    fun partitionRuns(hunk: UnifiedDiffParser.Hunk): HunkRuns {
        val added = mutableListOf<LineRun>()
        val removed = mutableListOf<LineRun>()

        var oldLine = hunk.oldStart
        var newLine = hunk.newStart

        var curAddedStart = -1
        var curAddedLines = mutableListOf<String>()
        var curRemovedStart = -1
        var curRemovedLines = mutableListOf<String>()

        fun flushAdded() {
            if (curAddedLines.isNotEmpty()) {
                added += LineRun(curAddedStart, curAddedLines.toList())
                curAddedLines = mutableListOf()
                curAddedStart = -1
            }
        }
        fun flushRemoved() {
            if (curRemovedLines.isNotEmpty()) {
                removed += LineRun(curRemovedStart, curRemovedLines.toList())
                curRemovedLines = mutableListOf()
                curRemovedStart = -1
            }
        }

        for (raw in hunk.body) {
            if (raw.isEmpty()) continue
            when (raw[0]) {
                ' ' -> {
                    flushAdded()
                    flushRemoved()
                    oldLine++
                    newLine++
                }
                '+' -> {
                    flushRemoved()
                    if (curAddedLines.isEmpty()) curAddedStart = newLine
                    curAddedLines += raw.substring(1)
                    newLine++
                }
                '-' -> {
                    flushAdded()
                    if (curRemovedLines.isEmpty()) curRemovedStart = oldLine
                    curRemovedLines += raw.substring(1)
                    oldLine++
                }
                '\\' -> { /* "\ No newline at end of file" — not a content line */ }
                else -> { /* Unknown prefix; ignore conservatively. */ }
            }
        }
        flushAdded()
        flushRemoved()

        return HunkRuns(hunk.header, added, removed)
    }
}
