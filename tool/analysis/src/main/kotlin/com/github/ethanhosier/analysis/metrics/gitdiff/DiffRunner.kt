package com.github.ethanhosier.analysis.metrics.gitdiff

import com.github.ethanhosier.analysis.reconstruct.GitRunner

/**
 * Computes per-checkpoint-transition diff stats by running
 * `git diff -M` against the previous checkpoint (or the seed commit for
 * the first one).
 *
 * Cheap compared to the build/test pass, so it runs sequentially after
 * the parallel state-metrics loop instead of borrowing a worktree.
 */
class DiffRunner(private val git: GitRunner) {

    fun runAll(orderedShas: List<String>): Map<String, DiffStats> {
        if (orderedShas.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, DiffStats>()
        for ((i, sha) in orderedShas.withIndex()) {
            val from = if (i == 0) git.parentOf(sha) else orderedShas[i - 1]
            out[sha] = if (from == null) DiffStats.ZERO else run(from, sha)
        }
        return out
    }

    private fun run(from: String, to: String): DiffStats {
        val numstat = git.diffNumstat(from, to).map(::parseNumstat)
        val nameStatus = git.diffNameStatus(from, to).map(::parseNameStatus)

        val churnByPath = numstat.associateBy { it.path }
        var linesAdded = 0
        var linesDeleted = 0
        var filesAdded = 0
        var filesModified = 0
        var filesDeleted = 0
        var filesRenamed = 0
        val perFile = ArrayList<PerFileChurn>(nameStatus.size)
        val touched = LinkedHashSet<String>()

        for (entry in nameStatus) {
            when (entry.status) {
                'A' -> filesAdded++
                'M' -> filesModified++
                'D' -> filesDeleted++
                'R', 'C' -> filesRenamed++
                else -> filesModified++
            }
            touched.add(entry.path)
            val churn = churnByPath[entry.path]
            val added = churn?.linesAdded ?: 0
            val deleted = churn?.linesDeleted ?: 0
            linesAdded += added
            linesDeleted += deleted
            perFile.add(PerFileChurn(entry.path, added, deleted))
        }

        return DiffStats(
            filesTouched = touched.size,
            linesAdded = linesAdded,
            linesDeleted = linesDeleted,
            totalChurn = linesAdded + linesDeleted,
            filesAdded = filesAdded,
            filesModified = filesModified,
            filesDeleted = filesDeleted,
            filesRenamed = filesRenamed,
            perFileChurn = perFile,
        )
    }

    private data class NumstatRow(val linesAdded: Int, val linesDeleted: Int, val path: String)
    private data class NameStatusRow(val status: Char, val path: String)

    // `added\tdeleted\tpath` or `added\tdeleted\told\tnew` for renames in -M output.
    private fun parseNumstat(line: String): NumstatRow {
        val parts = line.split('\t')
        val added = parts[0].toIntOrNull() ?: 0  // `-` for binary -> 0
        val deleted = parts[1].toIntOrNull() ?: 0
        val path = if (parts.size >= 4) parts[3] else parts[2]
        return NumstatRow(added, deleted, path)
    }

    // `A\tpath`, `M\tpath`, `D\tpath`, `R090\told\tnew`, `C080\told\tnew`.
    private fun parseNameStatus(line: String): NameStatusRow {
        val parts = line.split('\t')
        val status = parts[0][0]
        val path = if (parts.size >= 3) parts[2] else parts[1]
        return NameStatusRow(status, path)
    }
}
