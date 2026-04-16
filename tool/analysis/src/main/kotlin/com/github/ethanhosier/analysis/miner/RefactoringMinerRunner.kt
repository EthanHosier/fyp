package com.github.ethanhosier.analysis.miner

import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.miner.model.DetectedRefactoring
import com.github.ethanhosier.analysis.miner.model.ManualRefactoringSegment
import com.github.ethanhosier.analysis.miner.model.RefactoringFinding
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.model.Trace
import com.github.ethanhosier.ideplugin.model.EventType
import gr.uom.java.xmi.diff.CodeRange
import org.refactoringminer.api.Refactoring
import org.refactoringminer.api.RefactoringHandler
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runs RefactoringMiner on pairs of shadow-repo commits to detect
 * refactoring patterns in manual-edit intervals between automated IDE
 * refactorings.
 *
 * Two passes per segment (run between two consecutive anchors — an anchor
 * being a checkpoint reached via `SESSION_STARTED` or
 * `REFACTORING_FINISHED`):
 *
 *  - **Segment-level** — one RM run spanning `(A_L → last_manual)`.
 *    Captures the net refactorings the user made across the segment.
 *  - **Inner sliding window** — grows `R` forward from the first manual
 *    checkpoint; on the first non-empty detection, shrinks `L` forward while
 *    the detection set stays equal (canonical-keyed) to lock onto the
 *    tightest window `[L*, R*]`. Then restarts with `L = R*` and continues.
 *
 * RM is invoked via [GitHistoryRefactoringMinerImpl.detectAtDirectories]
 * against two worktrees borrowed from [WorktreePool] at the two SHAs — no
 * JGit code in our codebase.
 */
class RefactoringMinerRunner(
    private val parallelism: Int = defaultParallelism(),
) {

    data class Summary(
        val segmentsAnalysed: Int,
        val segments: List<ManualRefactoringSegment>,
    )

    fun run(
        trace: Trace,
        reconstruction: ReconstructionResult,
        sessionFolder: Path,
    ): Summary {
        val checkpoints = orderedUniqueShas(reconstruction)
        val anchors = anchorSet(trace, reconstruction)
        val segments = partitionSegments(checkpoints, anchors)

        if (segments.isEmpty()) return Summary(0, emptyList())

        // Two worktrees per RM call; a segment processes one call at a time,
        // so pool size = 2 × parallelism is enough.
        val poolSize = (parallelism * 2).coerceAtLeast(2)
        val worktreeBase = sessionFolder.resolve("refactoring-miner-worktrees")
        val pool = WorktreePool(reconstruction.repoDir, worktreeBase, poolSize)
        val executor = Executors.newFixedThreadPool(parallelism)

        val results = try {
            val futures = segments.mapIndexed { i, seg ->
                executor.submit<ManualRefactoringSegment?> { processSegment(i, seg, pool) }
            }
            futures.mapNotNull { future ->
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(1, TimeUnit.MINUTES)
            pool.close()
        }

        return Summary(segmentsAnalysed = segments.size, segments = results)
    }

    /**
     * Returns null if the segment had no manual checkpoints or no
     * refactorings at either scope — empty entries are not worth
     * surfacing.
     */
    private fun processSegment(
        index: Int,
        segment: Segment,
        pool: WorktreePool,
    ): ManualRefactoringSegment? {
        val manuals = segment.manuals
        if (manuals.isEmpty()) return null

        val segmentLevel = runRM(pool, segment.leftAnchor, manuals.last())

        val innerFindings = mutableListOf<RefactoringFinding>()
        // lIdx == -1 means L is the left anchor; otherwise L = manuals[lIdx].
        var lIdx = -1
        var rIdx = 0
        while (rIdx < manuals.size) {
            val lSha = if (lIdx == -1) segment.leftAnchor else manuals[lIdx]
            val rSha = manuals[rIdx]
            val detections = runRM(pool, lSha, rSha)
            if (detections.isEmpty()) {
                rIdx++
                continue
            }

            val targetKeys = detections.map(::canonicalKey).toSet()
            var tightestLSha = lSha
            var probeIdx = lIdx + 1
            while (probeIdx < rIdx) {
                val probeSha = manuals[probeIdx]
                val probeKeys = runRM(pool, probeSha, rSha).map(::canonicalKey).toSet()
                if (probeKeys == targetKeys) {
                    tightestLSha = probeSha
                    probeIdx++
                } else break
            }

            innerFindings.add(
                RefactoringFinding(
                    fromSha = tightestLSha,
                    toSha = rSha,
                    refactorings = detections.map(::toDetected),
                ),
            )

            // Restart: new baseline = locked R; continue from R+1.
            lIdx = rIdx
            rIdx++
        }

        if (segmentLevel.isEmpty() && innerFindings.isEmpty()) return null

        return ManualRefactoringSegment(
            segmentIndex = index,
            fromSha = segment.leftAnchor,
            toSha = manuals.last(),
            segmentRefactorings = segmentLevel.map(::toDetected),
            innerFindings = innerFindings,
        )
    }

    private fun runRM(pool: WorktreePool, fromSha: String, toSha: String): List<Refactoring> {
        val prev = pool.borrow(fromSha)
        val next = pool.borrow(toSha)
        return try {
            val collected = mutableListOf<Refactoring>()
            // RM's default handleException is a no-op — override so internal failures
            // surface instead of being silently treated as "no refactorings".
            var captured: Exception? = null
            GitHistoryRefactoringMinerImpl().detectAtDirectories(
                prev.toAbsolutePath(),
                next.toAbsolutePath(),
                object : RefactoringHandler {
                    override fun handle(commitId: String, refactorings: List<Refactoring>) {
                        collected.addAll(refactorings)
                    }
                    override fun handleException(commitId: String, e: Exception) {
                        captured = e
                    }
                },
            )
            captured?.let { throw IllegalStateException("RefactoringMiner failed on $fromSha → $toSha", it) }
            collected
        } finally {
            pool.release(next)
            pool.release(prev)
        }
    }

    private fun toDetected(r: Refactoring): DetectedRefactoring {
        val type = r.refactoringType.displayName
        return DetectedRefactoring(
            type = type,
            description = r.toString(),
            leftSideLocations = r.leftSide().map(::codeRangeKey),
            rightSideLocations = r.rightSide().map(::codeRangeKey),
            ideRelevant = IdeRelevantRefactorings.isIdeRelevant(type),
        )
    }

    // Canonical key: type + sorted left locations + sorted right locations.
    // Same field set as DetectedRefactoring.leftSideLocations/rightSideLocations
    // so the "same refactoring" relation used by the sliding window matches
    // what a reader sees in the serialized output.
    private fun canonicalKey(r: Refactoring): String {
        val left = r.leftSide().map(::codeRangeKey).sorted()
        val right = r.rightSide().map(::codeRangeKey).sorted()
        return "${r.refactoringType.displayName}|L=$left|R=$right"
    }

    private fun codeRangeKey(c: CodeRange): String =
        "${c.filePath}:${c.startLine}-${c.endLine} [${c.codeElementType}] ${c.codeElement}"

    private data class Segment(val leftAnchor: String, val manuals: List<String>)

    private fun orderedUniqueShas(reconstruction: ReconstructionResult): List<String> =
        reconstruction.eventCommits.mapping.values.toCollection(LinkedHashSet()).toList()

    private fun anchorSet(trace: Trace, reconstruction: ReconstructionResult): Set<String> {
        val anchors = mutableSetOf<String>()
        val seen = mutableSetOf<String>()
        val mapping = reconstruction.eventCommits.mapping
        for (event in trace.events) {
            val sha = mapping[event.id] ?: continue
            if (!seen.add(sha)) continue
            if (event.type == EventType.SESSION_STARTED || event.type == EventType.REFACTORING_FINISHED) {
                anchors.add(sha)
            }
        }
        return anchors
    }

    private fun partitionSegments(checkpoints: List<String>, anchors: Set<String>): List<Segment> {
        val segments = mutableListOf<Segment>()
        var currentAnchor: String? = null
        var currentManuals = mutableListOf<String>()
        for (sha in checkpoints) {
            if (sha in anchors) {
                if (currentAnchor != null && currentManuals.isNotEmpty()) {
                    segments.add(Segment(currentAnchor, currentManuals.toList()))
                }
                currentAnchor = sha
                currentManuals = mutableListOf()
            } else if (currentAnchor != null) {
                currentManuals.add(sha)
            }
        }
        if (currentAnchor != null && currentManuals.isNotEmpty()) {
            segments.add(Segment(currentAnchor, currentManuals.toList()))
        }
        return segments
    }

    companion object {
        fun defaultParallelism(): Int =
            (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    }
}
