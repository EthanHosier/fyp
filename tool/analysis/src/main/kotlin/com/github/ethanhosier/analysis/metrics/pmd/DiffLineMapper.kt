package com.github.ethanhosier.analysis.metrics.pmd

class DiffLineMapper private constructor(
    private val hunks: List<Hunk>,
    private val deltaBefore: IntArray,
) {

    sealed interface Mapped {
        data class Translated(val line: Int) : Mapped
        data object Deleted : Mapped
    }

    fun map(oldLine: Int): Mapped {
        require(oldLine >= 1) { "oldLine must be >= 1, was $oldLine" }
        if (hunks.isEmpty()) return Mapped.Translated(oldLine)

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
        val oldEnd: Int get() = oldStart + oldCount - 1
    }

    companion object {
        val IDENTITY = DiffLineMapper(emptyList(), IntArray(1))

        fun parse(unifiedDiff: String): DiffLineMapper {
            val raw = mutableListOf<Hunk>()
            for (line in unifiedDiff.lineSequence()) {
                if (!line.startsWith("@@")) continue
                val parsed = parseHunkHeader(line) ?: continue
                raw += parsed
            }
            return build(raw)
        }

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
