package com.github.ethanhosier.analysis.alternative.reorder

import com.github.ethanhosier.analysis.alternative.validate.RefactoringStepValidator
import com.github.ethanhosier.analysis.miner.model.RefactoringStep

/**
 * Pipeline-side glue that splits the mined [RefactoringStep] trace
 * into reordering windows and emits a [ReorderDebug.describe]
 * summary per window via the supplied logger.
 *
 * Windowing rule: any step whose [RefactoringStepValidator]
 * status is not [RefactoringStepValidator.Status.VALID] is a
 * **splitter** and is excluded from any window. This subsumes the
 * old "split on untyped" rule (untyped steps now classify as
 * [RefactoringStepValidator.Status.UNTYPED]) and additionally
 * breaks on apply failures and AST divergences — i.e. on every
 * step where our reapplication of the spec doesn't reproduce the
 * user's `toSha`. Reordering across such a step would mix in
 * effects we can't simulate.
 *
 * Read-only — no synthesis, no schema changes.
 */
object ReorderWindowLogger {

    data class Summary(
        /** Sub-windows produced after splitting on non-VALID steps. */
        val totalWindows: Int,
        /** Sub-windows with ≥2 VALID typed specs (analysed and logged). */
        val eligibleWindows: Int,
        /** Sub-windows with exactly 1 VALID typed spec (quietly skipped). */
        val singletonWindows: Int,
        /** Total VALID typed specs across all windows. */
        val typedCount: Int,
        /** Steps acting as splitters because their spec was untyped. */
        val untypedCount: Int,
        /** Steps acting as splitters because the AST diverged. */
        val divergentCount: Int,
        /** Steps acting as splitters because our reapplication failed. */
        val refactorFailedCount: Int,
    )

    fun log(
        steps: List<RefactoringStep>,
        validations: Map<Int, RefactoringStepValidator.StepValidation>,
        log: (String) -> Unit,
    ): Summary {
        val windows = splitOnInvalid(steps, validations, log)
        val typedCount = windows.sumOf { it.size }
        var untyped = 0
        var diverged = 0
        var failed = 0
        for (s in steps) {
            when (validations[s.stepIndex]?.status) {
                RefactoringStepValidator.Status.UNTYPED -> untyped++
                RefactoringStepValidator.Status.AST_DIVERGED -> diverged++
                RefactoringStepValidator.Status.REFACTOR_FAILED -> failed++
                else -> Unit
            }
        }
        var eligible = 0
        var singletons = 0
        for ((wIdx, window) in windows.withIndex()) {
            if (window.size < 2) {
                singletons++
                continue
            }
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
        return Summary(
            totalWindows = windows.size,
            eligibleWindows = eligible,
            singletonWindows = singletons,
            typedCount = typedCount,
            untypedCount = untyped,
            divergentCount = diverged,
            refactorFailedCount = failed,
        )
    }

    /**
     * Split [steps] on every step whose validation status is not
     * [RefactoringStepValidator.Status.VALID]. Steps missing from
     * [validations] are conservatively treated as splitters with an
     * `unvalidated` reason.
     */
    private fun splitOnInvalid(
        steps: List<RefactoringStep>,
        validations: Map<Int, RefactoringStepValidator.StepValidation>,
        log: (String) -> Unit,
    ): List<List<RefactoringStep>> {
        val windows = mutableListOf<List<RefactoringStep>>()
        var current = mutableListOf<RefactoringStep>()
        for (step in steps) {
            val v = validations[step.stepIndex]
            val isValid = v?.status == RefactoringStepValidator.Status.VALID
            if (isValid) {
                current.add(step)
            } else {
                if (current.isNotEmpty()) {
                    windows.add(current)
                    current = mutableListOf()
                }
                val status = v?.status?.name ?: "UNVALIDATED"
                val reason = v?.reason?.let { " — $it" } ?: ""
                log("reorder-log: split at step #${step.stepIndex} ($status$reason)")
            }
        }
        if (current.isNotEmpty()) windows.add(current)
        return windows
    }

    private fun shortSha(sha: String): String = if (sha.length <= 8) sha else sha.take(8)
}
