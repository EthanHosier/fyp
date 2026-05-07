package com.github.ethanhosier.analysis.alternative.reorder

import com.github.ethanhosier.analysis.miner.model.DetectedRefactoring
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReorderWindowLoggerTest {

    @Test
    fun `treats the whole trace as one window when nothing is untyped`() {
        val steps = listOf(
            step(0, "aaa", "bbb", renameClass("com.A", "AA")),
            step(1, "bbb", "ccc", renameClass("com.B", "BB")),
            step(2, "ccc", "ddd", renameClass("com.C", "CC")),
        )

        val lines = mutableListOf<String>()
        val summary = ReorderWindowLogger.log(steps) { lines += it }

        assertEquals(1, summary.totalWindows)
        assertEquals(1, summary.eligibleWindows)
        assertEquals(0, summary.singletonWindows)
        assertEquals(3, summary.typedCount)
        assertEquals(0, summary.untypedCount)

        assertTrue(lines.any { "window #0 aaa→ddd" in it && "3 typed specs" in it })
        // Three independent renames → no edges, 6 orderings.
        assertTrue(lines.any { "Edges: (none — all specs commute)" in it })
        assertTrue(lines.any { "Orderings (6 valid)" in it })
    }

    @Test
    fun `Other and null specs split the trace into separate windows`() {
        val steps = listOf(
            step(0, "aaa", "bbb", renameClass("com.A", "AA")),
            step(1, "bbb", "ccc", renameClass("com.B", "BB")),
            // splitter: untyped op the analyser can't reason across
            step(2, "ccc", "ddd", spec = RefactoringSpec.Other),
            step(3, "ddd", "eee", renameClass("com.C", "CC")),
            step(4, "eee", "fff", renameClass("com.D", "DD")),
            // splitter: null spec
            step(5, "fff", "ggg", spec = null),
            step(6, "ggg", "hhh", renameClass("com.E", "EE")),
            step(7, "hhh", "iii", renameClass("com.F", "FF")),
        )

        val lines = mutableListOf<String>()
        val summary = ReorderWindowLogger.log(steps) { lines += it }

        assertEquals(3, summary.totalWindows)
        assertEquals(3, summary.eligibleWindows)
        assertEquals(0, summary.singletonWindows)
        assertEquals(6, summary.typedCount)
        assertEquals(2, summary.untypedCount)

        // Three sub-windows, each on its own contiguous SHA range,
        // each enumerated independently.
        assertTrue(lines.any { "window #0 aaa→ccc" in it })
        assertTrue(lines.any { "window #1 ddd→fff" in it })
        assertTrue(lines.any { "window #2 ggg→iii" in it })
    }

    @Test
    fun `singletons after splitting are not logged but counted`() {
        // Trace: typed, splitter, typed, typed, splitter, typed.
        // Yields windows of sizes [1, 2, 1] — only the middle one logs.
        val steps = listOf(
            step(0, "aaa", "bbb", renameClass("com.A", "AA")),
            step(1, "bbb", "ccc", spec = RefactoringSpec.Other),
            step(2, "ccc", "ddd", renameClass("com.B", "BB")),
            step(3, "ddd", "eee", renameClass("com.C", "CC")),
            step(4, "eee", "fff", spec = RefactoringSpec.Other),
            step(5, "fff", "ggg", renameClass("com.D", "DD")),
        )

        val lines = mutableListOf<String>()
        val summary = ReorderWindowLogger.log(steps) { lines += it }

        assertEquals(3, summary.totalWindows)
        assertEquals(1, summary.eligibleWindows)
        assertEquals(2, summary.singletonWindows)
        assertEquals(4, summary.typedCount)
        assertEquals(2, summary.untypedCount)

        assertTrue(lines.any { "window #1 ccc→eee" in it && "2 typed specs" in it })
        // Singletons stay quiet.
        assertTrue(lines.none { "window #0" in it })
        assertTrue(lines.none { "window #2" in it })
    }

    @Test
    fun `consecutive untyped splitters do not create empty windows`() {
        val steps = listOf(
            step(0, "aaa", "bbb", renameClass("com.A", "AA")),
            step(1, "bbb", "ccc", renameClass("com.B", "BB")),
            step(2, "ccc", "ddd", spec = RefactoringSpec.Other),
            step(3, "ddd", "eee", spec = null),
            step(4, "eee", "fff", spec = RefactoringSpec.Other),
            step(5, "fff", "ggg", renameClass("com.C", "CC")),
            step(6, "ggg", "hhh", renameClass("com.D", "DD")),
        )

        val summary = ReorderWindowLogger.log(steps) { /* ignore */ }

        // Two non-empty windows separated by 3 splitters; no spurious empties.
        assertEquals(2, summary.totalWindows)
        assertEquals(2, summary.eligibleWindows)
        assertEquals(0, summary.singletonWindows)
        assertEquals(4, summary.typedCount)
        assertEquals(3, summary.untypedCount)
    }

    @Test
    fun `empty step list returns zero counts and emits nothing`() {
        val lines = mutableListOf<String>()
        val summary = ReorderWindowLogger.log(emptyList()) { lines += it }
        assertEquals(0, summary.totalWindows)
        assertEquals(0, summary.eligibleWindows)
        assertEquals(0, summary.singletonWindows)
        assertEquals(0, summary.typedCount)
        assertEquals(0, summary.untypedCount)
        assertTrue(lines.isEmpty())
    }

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
