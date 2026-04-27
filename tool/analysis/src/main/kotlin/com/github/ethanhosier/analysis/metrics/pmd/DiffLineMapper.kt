package com.github.ethanhosier.analysis.metrics.pmd

/**
 * Translates 1-based old-side line numbers to their position on the new side
 * of a unified diff (`git diff -U0` between two SHAs for one file).
 *
 * Three behaviours are encoded:
 *  - lines outside any hunk shift by the cumulative net delta of preceding
 *    hunks (insertions push everything below them down, deletions pull up);
 *  - lines inside an *equal-count* hunk (`-N +N`) are paired 1:1 in order —
 *    the in-place-edit heuristic, since unified-diff has no separate
 *    "modified" marker and writes such edits as a `-` then `+` block;
 *  - lines inside any other hunk are reported as [Mapped.Deleted], because
 *    pairing is genuinely ambiguous when the hunk is a real rewrite.
 *
 * This is the layer-3c half of `PmdViolationTracker`: it answers "where did
 * old line X end up?" without any knowledge of PMD or violations, so the
 * matching logic can stay testable in isolation.
 *
 * Hunks are precomputed once at construction and queried via binary search,
 * so `map` is `O(log h)` per call regardless of how many violations a file
 * carries.
 */
class DiffLineMapper private constructor(
    /** Sorted ascending by `oldStart`. Empty for the identity mapper. */
    private val hunks: List<Hunk>,
    /**
     * `deltaBefore[i]` is the sum of `(newCount - oldCount)` for hunks
     * `[0..i)` — i.e. the cumulative shift to apply to any old line that
     * falls *before* `hunks[i]` and *after* `hunks[i - 1]`. Length =
     * `hunks.size + 1`; the last entry is the shift that applies to lines
     * past the final hunk.
     */
    private val deltaBefore: IntArray,
) {

    sealed interface Mapped {
        data class Translated(val line: Int) : Mapped
        data object Deleted : Mapped
    }

    /**
     * @param oldLine 1-based line number on the old side
     * @return [Mapped.Translated] for surviving lines, [Mapped.Deleted]
     *   when the line was removed or was part of an unequal-count rewrite
     */
    fun map(oldLine: Int): Mapped {
        require(oldLine >= 1) { "oldLine must be >= 1, was $oldLine" }
        if (hunks.isEmpty()) return Mapped.Translated(oldLine)

        // Binary search for the first hunk whose oldStart > oldLine. The
        // candidate hunk that *might* contain oldLine is the one immediately
        // before that index — if any.
        var lo = 0
        var hi = hunks.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (hunks[mid].oldStart > oldLine) hi = mid else lo = mid + 1
        }
        val candidateIdx = lo - 1
        if (candidateIdx >= 0) {
            val h = hunks[candidateIdx]
            if (oldLine <= h.oldEnd) {
                return if (h.oldCount == h.newCount && h.oldCount > 0) {
                    Mapped.Translated(h.newStart + (oldLine - h.oldStart))
                } else {
                    Mapped.Deleted
                }
            }
        }
        // Outside every hunk — apply cumulative delta of all preceding hunks.
        return Mapped.Translated(oldLine + deltaBefore[lo])
    }

    private data class Hunk(
        val oldStart: Int,
        val oldCount: Int,
        val newStart: Int,
        val newCount: Int,
    ) {
        // Inclusive last old-side line covered. For a zero-count hunk
        // (pure insertion at oldStart) there is no covered old line, so
        // `oldEnd < oldStart` and the containment check is never true.
        val oldEnd: Int get() = oldStart + oldCount - 1
    }

    companion object {
        /**
         * Identity mapper — used when no diff is available (e.g. the file
         * is unchanged between the two checkpoints), so every line maps
         * to itself.
         */
        val IDENTITY = DiffLineMapper(emptyList(), IntArray(1))

        /**
         * Parses a unified diff (single-file or multi-file) and returns a
         * mapper built from its hunk headers. Body lines are ignored — only
         * `@@ -a,b +c,d @@` matters for the offset arithmetic.
         */
        fun parse(unifiedDiff: String): DiffLineMapper {
            val raw = mutableListOf<Hunk>()
            for (line in unifiedDiff.lineSequence()) {
                if (!line.startsWith("@@")) continue
                val parsed = parseHunkHeader(line) ?: continue
                raw += parsed
            }
            return build(raw)
        }

        /**
         * Splits a multi-file unified diff and returns a mapper built from
         * the hunks for `relPath` (matched on the new-side path in the
         * `+++ b/<relPath>` line, with `b/` prefix tolerated). Returns
         * [IDENTITY] when the file isn't found in the diff — i.e. it
         * wasn't touched between the two checkpoints.
         */
        fun forFile(unifiedDiff: String, relPath: String): DiffLineMapper {
            if (unifiedDiff.isEmpty()) return IDENTITY
            val matchKey = relPath.removePrefix("/")
            val section = splitByFile(unifiedDiff).firstOrNull { (path, _) ->
                path == matchKey || path.removePrefix("b/") == matchKey
            } ?: return IDENTITY
            return parse(section.second)
        }

        private fun build(raw: List<Hunk>): DiffLineMapper {
            if (raw.isEmpty()) return IDENTITY
            val sorted = raw.sortedBy { it.oldStart }
            val deltas = IntArray(sorted.size + 1)
            for (i in sorted.indices) {
                deltas[i + 1] = deltas[i] + (sorted[i].newCount - sorted[i].oldCount)
            }
            return DiffLineMapper(sorted, deltas)
        }

        // `@@ -a,b +c,d @@ optional-context` — `b` and `d` default to 1
        // when omitted (single-line ranges).
        private fun parseHunkHeader(line: String): Hunk? {
            val core = line.removePrefix("@@").substringBefore("@@").trim()
            val parts = core.split(' ')
            if (parts.size < 2) return null
            val (oldStart, oldCount) = parseRange(parts[0].removePrefix("-")) ?: return null
            val (newStart, newCount) = parseRange(parts[1].removePrefix("+")) ?: return null
            return Hunk(oldStart, oldCount, newStart, newCount)
        }

        private fun parseRange(spec: String): Pair<Int, Int>? {
            val pieces = spec.split(',', limit = 2)
            val start = pieces[0].toIntOrNull() ?: return null
            val count = if (pieces.size == 2) pieces[1].toIntOrNull() ?: return null else 1
            return start to count
        }

        // Splits a multi-file unified-diff text into (newPath, sectionText)
        // pairs by walking `diff --git` markers. The section runs until the
        // next `diff --git` (or EOF).
        private fun splitByFile(diff: String): List<Pair<String, String>> {
            val out = mutableListOf<Pair<String, String>>()
            val lines = diff.lines()
            var i = 0
            while (i < lines.size) {
                if (!lines[i].startsWith("diff --git ")) { i++; continue }
                val start = i
                var path: String? = null
                var j = i + 1
                while (j < lines.size && !lines[j].startsWith("diff --git ")) {
                    if (lines[j].startsWith("+++ ")) {
                        path = lines[j].removePrefix("+++ ").trim()
                            .removePrefix("b/").takeIf { it != "/dev/null" }
                    }
                    j++
                }
                if (path != null) {
                    out += path to lines.subList(start, j).joinToString("\n")
                }
                i = j
            }
            return out
        }
    }
}
