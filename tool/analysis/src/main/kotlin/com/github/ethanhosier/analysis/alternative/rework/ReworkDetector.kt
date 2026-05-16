package com.github.ethanhosier.analysis.alternative.rework

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * Chunk-level rework detector. Given each step's unified diff plus
 * the pre- and post-state contents of every touched Java file, finds
 * pairs `(originating step k', terminal step k)` where the user
 * adds (or removes) a contiguous chunk at `k'` and reverses it at
 * some later `k` within the same `(file, enclosing scope)`.
 *
 * Pure function over its input — no git, no filesystem. The caller
 * is responsible for assembling [StepInput] records, which decouples
 * detection from the I/O of fetching patches and file contents.
 *
 * Algorithm summary:
 *  1. Per step, per touched file: extract added- and removed-runs
 *     via [HunkExtractor] and resolve each run's enclosing scope
 *     via [EnclosingScopeResolver].
 *  2. Normalize each run's lines (trim trailing whitespace, drop
 *     pure-whitespace lines) and hash to produce a content-addressed
 *     chunk key.
 *  3. Group chunks by `(file, scopeId, contentHash)`. Within each
 *     group, sort by step and pair opposite-side chunks greedily
 *     (first add ↔ first later remove of same hash, etc.). Each
 *     chunk participates in at most one pair.
 *  4. Aggregate pairs that share `(originating, terminal, file,
 *     scopeId)` into a single [ReworkFinding] with summed
 *     `lineCount`.
 *
 * Returns internal [ReworkFinding] records; mapping to the public
 * `DivergencePoint` schema is the pipeline-wiring layer's job.
 */
object ReworkDetector {

    enum class Side { ADDED, REMOVED }
    @Serializable
    enum class Direction {
        /** User added the chunk at `originatingStep`, removed it at `terminalStep`. */
        ADD_THEN_REMOVE,
        /** User removed the chunk at `originatingStep`, added it back at `terminalStep`. */
        REMOVE_THEN_ADD,
    }

    data class StepInput(
        val stepIndex: Int,
        /** Unified-diff text from `git diff -U0 preSha postSha -- file`. */
        val patch: String,
        /** File path → pre-state content. Required for every file referenced
         *  by a removed-run's scope resolution. */
        val preFileContent: Map<String, String>,
        /** File path → post-state content. Required for every file
         *  referenced by an added-run's scope resolution. */
        val postFileContent: Map<String, String>,
    )

    data class ReworkFinding(
        val originatingStep: Int,
        val terminalStep: Int,
        val file: String,
        val scopeId: String,
        val direction: Direction,
        /** Sum of `lineCount` across all chunk pairs sharing
         *  `(originatingStep, terminalStep, file, scopeId)`. */
        val lineCount: Int,
        /** Truncated body of the first matched chunk for display. */
        val contentSummary: String,
    )

    /**
     * One unaggregated rework match: the +run (or -run, depending on
     * [direction]) at the originating step paired with its opposite-
     * side counterpart at the terminal step. Carries the exact run
     * positions and raw line counts that the alt-builder needs for
     * surgery and zone registration.
     */
    data class ChunkPair(
        val originatingStep: Int,
        val terminalStep: Int,
        val file: String,
        val scopeId: String,
        val direction: Direction,
        /** Run startLine in the relevant side of the originating
         *  step's diff (newLine for `ADD_THEN_REMOVE`, oldLine for
         *  `REMOVE_THEN_ADD`). */
        val originatingRunStartLine: Int,
        /** Run startLine in the relevant side of the terminal step's
         *  diff (oldLine for `ADD_THEN_REMOVE`, newLine for
         *  `REMOVE_THEN_ADD`). */
        val terminalRunStartLine: Int,
        /** Raw run length, *before* normalisation — what the drift
         *  tracker needs to size the zone. May exceed
         *  [normalizedLineCount] when the run contains blank lines. */
        val rawLineCount: Int,
        /** Line count after dropping blank / pure-whitespace lines —
         *  what gets summed into a [ReworkFinding]'s aggregate
         *  `lineCount` for display. */
        val normalizedLineCount: Int,
        val contentSummary: String,
        /** Full chunk body — the raw lines joined by `\n`, *without*
         *  the `+`/`-` diff prefix. Used to synthesise focused
         *  originating / terminal unified-diff patches for display. */
        val chunkSourceText: String,
    )

    /**
     * Default minimum normalized line count for a chunk to participate
     * in matching. Single-line chunks are filtered out by default to
     * suppress the documented v1 false-positive class — two repeated
     * identical 1-line statements in the same scope are
     * content-indistinguishable, and the pairing can attribute the
     * wrong (add, delete) instances. Pass `1` to disable.
     */
    const val DEFAULT_MIN_NORMALIZED_LINE_COUNT: Int = 2

    fun detect(
        steps: List<StepInput>,
        minNormalizedLineCount: Int = DEFAULT_MIN_NORMALIZED_LINE_COUNT,
    ): List<ReworkFinding> {
        val pairs = collectPairs(steps, minNormalizedLineCount)
        return aggregate(pairs)
    }

    /**
     * Returns the unaggregated chunk pairs detected — one [ChunkPair]
     * per matched (originating chunk, terminal chunk) without the
     * `(originatingStep, terminalStep, file, scopeId)` aggregation
     * applied by [detect]. Consumed by the alt-builder, which needs
     * exact run positions to drive surgery.
     */
    fun detectChunkPairs(
        steps: List<StepInput>,
        minNormalizedLineCount: Int = DEFAULT_MIN_NORMALIZED_LINE_COUNT,
    ): List<ChunkPair> =
        collectPairs(steps, minNormalizedLineCount).map { (earlier, later) ->
            val direction = if (earlier.side == Side.ADDED) Direction.ADD_THEN_REMOVE else Direction.REMOVE_THEN_ADD
            ChunkPair(
                originatingStep = earlier.stepIndex,
                terminalStep = later.stepIndex,
                file = earlier.file,
                scopeId = earlier.scopeId,
                direction = direction,
                originatingRunStartLine = earlier.startLine,
                terminalRunStartLine = later.startLine,
                rawLineCount = earlier.rawLineCount,
                normalizedLineCount = earlier.lineCount,
                contentSummary = earlier.sourceText.take(80),
                chunkSourceText = earlier.sourceText,
            )
        }.sortedWith(compareBy({ it.terminalStep }, { it.originatingStep }, { it.file }))

    private fun collectPairs(
        steps: List<StepInput>,
        minNormalizedLineCount: Int,
    ): List<Pair<EditChunk, EditChunk>> {
        val chunks = steps.flatMap { extractChunksFromStep(it) }
            .filter { it.lineCount >= minNormalizedLineCount }
        val grouped = chunks.groupBy { Triple(it.file, it.scopeId, it.contentHash) }
        return grouped.values.flatMap { pairChunks(it) }
    }

    private data class EditChunk(
        val stepIndex: Int,
        val file: String,
        val scopeId: String,
        val contentHash: String,
        val sourceText: String,
        /** Normalised line count (blanks dropped) — used for aggregate magnitude. */
        val lineCount: Int,
        /** Raw run length — used for surgery / zone sizing. */
        val rawLineCount: Int,
        /** First line of the run on the relevant side (new for ADDED, old for REMOVED). */
        val startLine: Int,
        val side: Side,
    )

    private fun extractChunksFromStep(step: StepInput): List<EditChunk> {
        val out = mutableListOf<EditChunk>()
        for (fileHunks in HunkExtractor.extractFromPatch(step.patch)) {
            val newPath = fileHunks.newPath
            val oldPath = fileHunks.oldPath
            val displayPath = newPath ?: oldPath ?: continue
            if (!displayPath.endsWith(".java")) continue

            val postSrc = newPath?.let { step.postFileContent[it] }
            val preSrc = oldPath?.let { step.preFileContent[it] }

            for (hunk in fileHunks.hunks) {
                for (run in hunk.addedRuns) {
                    val joined = run.lines.joinToString("\n")
                    val (hash, lineCount) = normalizeAndHash(run.lines)
                    if (lineCount == 0) continue
                    val scopeId = postSrc?.let {
                        EnclosingScopeResolver.resolve(it, displayPath, run.startLine)
                    } ?: "$displayPath#<file>"
                    out += EditChunk(
                        stepIndex = step.stepIndex,
                        file = displayPath,
                        scopeId = scopeId,
                        contentHash = hash,
                        sourceText = joined,
                        lineCount = lineCount,
                        rawLineCount = run.lines.size,
                        startLine = run.startLine,
                        side = Side.ADDED,
                    )
                }
                for (run in hunk.removedRuns) {
                    val joined = run.lines.joinToString("\n")
                    val (hash, lineCount) = normalizeAndHash(run.lines)
                    if (lineCount == 0) continue
                    val scopeId = preSrc?.let {
                        EnclosingScopeResolver.resolve(it, displayPath, run.startLine)
                    } ?: "$displayPath#<file>"
                    out += EditChunk(
                        stepIndex = step.stepIndex,
                        file = displayPath,
                        scopeId = scopeId,
                        contentHash = hash,
                        sourceText = joined,
                        lineCount = lineCount,
                        rawLineCount = run.lines.size,
                        startLine = run.startLine,
                        side = Side.REMOVED,
                    )
                }
            }
        }
        return out
    }

    /**
     * Normalize a run's lines per the plan: strip trailing whitespace
     * on each line, drop pure-whitespace lines, join with `\n`, hash.
     * Returns the hash and the number of surviving lines (used as the
     * chunk's `lineCount`).
     */
    private fun normalizeAndHash(lines: List<String>): Pair<String, Int> {
        val normalized = lines
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
        val joined = normalized.joinToString("\n")
        return sha256Hex16(joined) to normalized.size
    }

    private fun sha256Hex16(s: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        val sb = StringBuilder(16)
        for (i in 0 until 8) {
            val b = bytes[i].toInt() and 0xff
            sb.append("%02x".format(b))
        }
        return sb.toString()
    }

    /**
     * Pairs chunks within a single `(file, scope, hash)` group by step
     * order: each chunk is paired with the earliest later chunk of the
     * opposite side. A single-sided pending queue suffices because
     * whenever a chunk of opposite side arrives, it pairs immediately
     * with the head — leaving the queue single-sided by induction.
     */
    private fun pairChunks(chunks: List<EditChunk>): List<Pair<EditChunk, EditChunk>> {
        val sorted = chunks.sortedBy { it.stepIndex }
        val pending = ArrayDeque<EditChunk>()
        val pairs = mutableListOf<Pair<EditChunk, EditChunk>>()
        for (c in sorted) {
            val head = pending.firstOrNull()
            if (head != null && head.side != c.side) {
                pairs += head to c
                pending.removeFirst()
            } else {
                pending.addLast(c)
            }
        }
        return pairs
    }

    private fun aggregate(pairs: List<Pair<EditChunk, EditChunk>>): List<ReworkFinding> {
        if (pairs.isEmpty()) return emptyList()

        data class Key(val originating: Int, val terminal: Int, val file: String, val scopeId: String, val direction: Direction)

        val grouped = mutableMapOf<Key, MutableList<Pair<EditChunk, EditChunk>>>()
        for (p in pairs) {
            val (earlier, later) = p
            val direction = if (earlier.side == Side.ADDED) Direction.ADD_THEN_REMOVE else Direction.REMOVE_THEN_ADD
            val key = Key(earlier.stepIndex, later.stepIndex, earlier.file, earlier.scopeId, direction)
            grouped.getOrPut(key) { mutableListOf() } += p
        }

        return grouped.map { (key, ps) ->
            val totalLines = ps.sumOf { it.first.lineCount }
            val summary = ps.first().first.sourceText.take(80)
            ReworkFinding(
                originatingStep = key.originating,
                terminalStep = key.terminal,
                file = key.file,
                scopeId = key.scopeId,
                direction = key.direction,
                lineCount = totalLines,
                contentSummary = summary,
            )
        }.sortedWith(compareBy({ it.terminalStep }, { it.originatingStep }, { it.file }, { it.scopeId }))
    }
}
