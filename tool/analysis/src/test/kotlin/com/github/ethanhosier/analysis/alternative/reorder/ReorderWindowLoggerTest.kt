package com.github.ethanhosier.analysis.alternative.reorder

import com.github.ethanhosier.analysis.alternative.validate.RefactoringStepValidator
import com.github.ethanhosier.analysis.alternative.validate.RefactoringStepValidator.Status
import com.github.ethanhosier.analysis.alternative.validate.RefactoringStepValidator.StepValidation
import com.github.ethanhosier.analysis.miner.model.DetectedRefactoring
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReorderWindowLoggerTest {

    @Test
    fun `treats the whole trace as one window when every step is VALID`() {
        val steps = listOf(
            step(0, "aaa", "bbb", renameClass("com.A", "AA")),
            step(1, "bbb", "ccc", renameClass("com.B", "BB")),
            step(2, "ccc", "ddd", renameClass("com.C", "CC")),
        )
        val validations = allValid(steps)

        val lines = mutableListOf<String>()
        val summary = ReorderWindowLogger.log(steps, validations) { lines += it }

        assertEquals(1, summary.totalWindows)
        assertEquals(1, summary.eligibleWindows)
        assertEquals(0, summary.singletonWindows)
        assertEquals(3, summary.typedCount)
        assertEquals(0, summary.untypedCount)
        assertEquals(0, summary.divergentCount)
        assertEquals(0, summary.refactorFailedCount)

        assertTrue(lines.any { "window #0 aaa→ddd" in it && "3 typed specs" in it })
        assertTrue(lines.any { "Edges: (none — all specs commute)" in it })
        assertTrue(lines.any { "Orderings (6 valid)" in it })
    }

    @Test
    fun `untyped steps split the trace into separate windows`() {
        val steps = listOf(
            step(0, "aaa", "bbb", renameClass("com.A", "AA")),
            step(1, "bbb", "ccc", renameClass("com.B", "BB")),
            step(2, "ccc", "ddd", spec = RefactoringSpec.Other),
            step(3, "ddd", "eee", renameClass("com.C", "CC")),
            step(4, "eee", "fff", renameClass("com.D", "DD")),
            step(5, "fff", "ggg", spec = null),
            step(6, "ggg", "hhh", renameClass("com.E", "EE")),
            step(7, "hhh", "iii", renameClass("com.F", "FF")),
        )
        val validations = mapOf(
            0 to validation(0, Status.VALID),
            1 to validation(1, Status.VALID),
            2 to validation(2, Status.UNTYPED, reason = "RefactoringSpec.Other"),
            3 to validation(3, Status.VALID),
            4 to validation(4, Status.VALID),
            5 to validation(5, Status.UNTYPED, reason = "no typed RefactoringSpec"),
            6 to validation(6, Status.VALID),
            7 to validation(7, Status.VALID),
        )

        val lines = mutableListOf<String>()
        val summary = ReorderWindowLogger.log(steps, validations) { lines += it }

        assertEquals(3, summary.totalWindows)
        assertEquals(3, summary.eligibleWindows)
        assertEquals(0, summary.singletonWindows)
        assertEquals(6, summary.typedCount)
        assertEquals(2, summary.untypedCount)
        assertEquals(0, summary.divergentCount)

        assertTrue(lines.any { "window #0 aaa→ccc" in it })
        assertTrue(lines.any { "window #1 ddd→fff" in it })
        assertTrue(lines.any { "window #2 ggg→iii" in it })
        // Split lines mention the splitter status.
        assertTrue(lines.any { "split at step #2 (UNTYPED" in it })
        assertTrue(lines.any { "split at step #5 (UNTYPED" in it })
    }

    @Test
    fun `ast diverged step acts as splitter and is counted`() {
        val steps = listOf(
            step(0, "aaa", "bbb", renameClass("com.A", "AA")),
            step(1, "bbb", "ccc", renameClass("com.B", "BB")), // diverged
            step(2, "ccc", "ddd", renameClass("com.C", "CC")),
            step(3, "ddd", "eee", renameClass("com.D", "DD")),
        )
        val validations = mapOf(
            0 to validation(0, Status.VALID),
            1 to validation(1, Status.AST_DIVERGED, reason = "file-content mismatch"),
            2 to validation(2, Status.VALID),
            3 to validation(3, Status.VALID),
        )

        val lines = mutableListOf<String>()
        val summary = ReorderWindowLogger.log(steps, validations) { lines += it }

        assertEquals(2, summary.totalWindows)
        assertEquals(1, summary.eligibleWindows)
        assertEquals(1, summary.singletonWindows)
        assertEquals(3, summary.typedCount) // VALID typed specs across all windows
        assertEquals(1, summary.divergentCount)
        assertEquals(0, summary.refactorFailedCount)
        assertTrue(lines.any { "split at step #1 (AST_DIVERGED — file-content mismatch)" in it })
    }

    @Test
    fun `refactor failed step acts as splitter and is counted`() {
        val steps = listOf(
            step(0, "aaa", "bbb", renameClass("com.A", "AA")),
            step(1, "bbb", "ccc", renameClass("com.B", "BB")),
            step(2, "ccc", "ddd", renameClass("com.C", "CC")), // failed
            step(3, "ddd", "eee", renameClass("com.D", "DD")),
            step(4, "eee", "fff", renameClass("com.E", "EE")),
        )
        val validations = mapOf(
            0 to validation(0, Status.VALID),
            1 to validation(1, Status.VALID),
            2 to validation(2, Status.REFACTOR_FAILED, reason = "anchor not found"),
            3 to validation(3, Status.VALID),
            4 to validation(4, Status.VALID),
        )

        val lines = mutableListOf<String>()
        val summary = ReorderWindowLogger.log(steps, validations) { lines += it }

        assertEquals(2, summary.totalWindows)
        assertEquals(2, summary.eligibleWindows)
        assertEquals(0, summary.singletonWindows)
        assertEquals(4, summary.typedCount)
        assertEquals(1, summary.refactorFailedCount)
        assertTrue(lines.any { "split at step #2 (REFACTOR_FAILED — anchor not found)" in it })
    }

    @Test
    fun `singletons after splitting are counted but not logged as windows`() {
        val steps = listOf(
            step(0, "aaa", "bbb", renameClass("com.A", "AA")),
            step(1, "bbb", "ccc", renameClass("com.B", "BB")), // splitter (diverged)
            step(2, "ccc", "ddd", renameClass("com.C", "CC")),
            step(3, "ddd", "eee", renameClass("com.D", "DD")),
            step(4, "eee", "fff", renameClass("com.E", "EE")), // splitter (diverged)
            step(5, "fff", "ggg", renameClass("com.F", "FF")),
        )
        val validations = mapOf(
            0 to validation(0, Status.VALID),
            1 to validation(1, Status.AST_DIVERGED),
            2 to validation(2, Status.VALID),
            3 to validation(3, Status.VALID),
            4 to validation(4, Status.AST_DIVERGED),
            5 to validation(5, Status.VALID),
        )

        val lines = mutableListOf<String>()
        val summary = ReorderWindowLogger.log(steps, validations) { lines += it }

        assertEquals(3, summary.totalWindows)
        assertEquals(1, summary.eligibleWindows)
        assertEquals(2, summary.singletonWindows)
        assertEquals(4, summary.typedCount)
        assertEquals(2, summary.divergentCount)

        assertTrue(lines.any { "window #1 ccc→eee" in it && "2 typed specs" in it })
        assertTrue(lines.none { "window #0 aaa" in it })
        assertTrue(lines.none { "window #2 fff" in it })
    }

    @Test
    fun `step missing from validations map is treated as splitter`() {
        val steps = listOf(
            step(0, "aaa", "bbb", renameClass("com.A", "AA")),
            step(1, "bbb", "ccc", renameClass("com.B", "BB")),
        )
        val lines = mutableListOf<String>()
        val summary = ReorderWindowLogger.log(steps, validations = emptyMap()) { lines += it }
        assertEquals(0, summary.totalWindows)
        assertEquals(0, summary.eligibleWindows)
        assertTrue(lines.any { "split at step #0 (UNVALIDATED)" in it })
    }

    @Test
    fun `empty step list returns zero counts and emits nothing`() {
        val lines = mutableListOf<String>()
        val summary = ReorderWindowLogger.log(emptyList(), emptyMap()) { lines += it }
        assertEquals(0, summary.totalWindows)
        assertEquals(0, summary.eligibleWindows)
        assertEquals(0, summary.singletonWindows)
        assertEquals(0, summary.typedCount)
        assertEquals(0, summary.untypedCount)
        assertEquals(0, summary.divergentCount)
        assertEquals(0, summary.refactorFailedCount)
        assertTrue(lines.isEmpty())
    }

    private fun validation(idx: Int, status: Status, reason: String? = null) =
        StepValidation(stepIndex = idx, status = status, reason = reason, divergedFiles = null)

    private fun allValid(steps: List<RefactoringStep>): Map<Int, StepValidation> =
        steps.associate { it.stepIndex to validation(it.stepIndex, Status.VALID) }

    private fun step(idx: Int, fromSha: String, toSha: String, spec: RefactoringSpec?): RefactoringStep =
        RefactoringStep(
            stepIndex = idx,
            fromSha = fromSha,
            toSha = toSha,
            toCheckpointIndex = idx,
            timestamp = idx.toLong(),
            refactoring = DetectedRefactoring(
                type = "Rename Class",
                description = "Rename Class (test)",
                leftSideLocations = emptyList(),
                rightSideLocations = emptyList(),
                ideRelevant = true,
            ),
            spec = spec,
        )

    private fun renameClass(typeFqn: String, newName: String): RefactoringSpec.RenameClass =
        RefactoringSpec.RenameClass(typeFqn = typeFqn, newName = newName)
}
