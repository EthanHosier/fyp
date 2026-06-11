package com.github.ethanhosier.analysis.alternative.rework

import kotlinx.serialization.Serializable
import java.security.MessageDigest

object ReworkDetector {

    enum class Side { ADDED, REMOVED }
    @Serializable
    enum class Direction {
        ADD_THEN_REMOVE,
        REMOVE_THEN_ADD,
    }

    data class StepInput(
        val stepIndex: Int,
        val patch: String,
        val preFileContent: Map<String, String>,
        val postFileContent: Map<String, String>,
    )

    data class ReworkFinding(
        val originatingStep: Int,
        val terminalStep: Int,
        val file: String,
        val scopeId: String,
        val direction: Direction,
        val lineCount: Int,
        val contentSummary: String,
    )

    data class ChunkPair(
        val originatingStep: Int,
        val terminalStep: Int,
        val file: String,
        val scopeId: String,
        val direction: Direction,
        val originatingRunStartLine: Int,
        val terminalRunStartLine: Int,
        val rawLineCount: Int,
        val normalizedLineCount: Int,
        val contentSummary: String,
        val chunkSourceText: String,
    )

    const val DEFAULT_MIN_NORMALIZED_LINE_COUNT: Int = 2

    fun detect(
        steps: List<StepInput>,
        minNormalizedLineCount: Int = DEFAULT_MIN_NORMALIZED_LINE_COUNT,
    ): List<ReworkFinding> {
        val pairs = collectPairs(steps, minNormalizedLineCount)
        return aggregate(pairs)
    }

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
        val lineCount: Int,
        val rawLineCount: Int,
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
