package com.github.ethanhosier.analysis.alternative.reorder

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep

/**
 * Pipeline-side glue that splits the mined [RefactoringStep] trace
 * into reordering windows and emits a [ReorderDebug.describe]
 * summary per window via the supplied logger.
 *
 * Rationale on windowing:
 *  - RM's per-step `(fromSha, toSha)` are *localisation* brackets
 *    (the tightest pair where the op shows up), **not** reordering
 *    boundaries.
 *  - The natural reorder window is the full ordered trace of typed
 *    specs.
 *  - But an untyped step ([RefactoringSpec.Other] or `null`) is an
 *    op the analyser can't reason about — its effects on entities
 *    are unknown — so reordering *across* it could silently produce
 *    invalid orderings. We therefore treat each untyped step as a
 *    window splitter: trace → list of contiguous typed sub-traces,
 *    with the untyped op preserving its position between sibling
 *    windows.
 *
 * Read-only — no synthesis, no schema changes.
 */
object ReorderWindowLogger {

    data class Summary(
        /** Sub-windows produced after splitting on untyped specs (any size). */
        val totalWindows: Int,
        /** Sub-windows with ≥2 typed specs (analysed and logged). */
        val eligibleWindows: Int,
        /** Sub-windows with exactly 1 typed spec (quietly skipped). */
        val singletonWindows: Int,
        /** Total typed specs across all windows. */
        val typedCount: Int,
        /** Number of untyped / Other steps acting as splitters. */
        val untypedCount: Int,
    )

    fun log(steps: List<RefactoringStep>, log: (String) -> Unit): Summary {
        val windows = splitOnUntyped(steps)
        val typedCount = windows.sumOf { it.size }
        val untypedCount = steps.size - typedCount
        var eligible = 0
        var singletons = 0

        for ((wIdx, window) in windows.withIndex()) {
            when {
                window.size < 2 -> {
                    singletons++
                    // 1-spec windows have only one ordering — quiet.
                }
                else -> {
                    eligible++
                    val from = shortSha(window.first().fromSha)
                    val to = shortSha(window.last().toSha)
                    val indices = window.map { it.stepIndex }
                    log(
                        "reorder-log: window #$wIdx $from→$to " +
                            "(${window.size} typed specs, step indices=$indices):",
                    )
                    val specs = window.mapNotNull { it.spec }
                    ReorderDebug.describe(specs).lineSequence().forEach { line ->
                        log("  $line")
                    }
                }
            }
        }

        return Summary(
            totalWindows = windows.size,
            eligibleWindows = eligible,
            singletonWindows = singletons,
            typedCount = typedCount,
            untypedCount = untypedCount,
        )
    }

    /**
     * Split [steps] on every untyped step (`null` spec or
     * [RefactoringSpec.Other]). Returns a list of contiguous
     * typed-only sub-traces, possibly empty when the trace has no
     * typed specs.
     */
    private fun splitOnUntyped(steps: List<RefactoringStep>): List<List<RefactoringStep>> {
        val windows = mutableListOf<List<RefactoringStep>>()
        var current = mutableListOf<RefactoringStep>()
        for (step in steps) {
            if (step.spec == null || step.spec == RefactoringSpec.Other) {
                if (current.isNotEmpty()) {
                    windows.add(current)
                    current = mutableListOf()
                }
            } else {
                current.add(step)
            }
        }
        if (current.isNotEmpty()) windows.add(current)
        return windows
    }

    private fun shortSha(sha: String): String = if (sha.length <= 8) sha else sha.take(8)
}
