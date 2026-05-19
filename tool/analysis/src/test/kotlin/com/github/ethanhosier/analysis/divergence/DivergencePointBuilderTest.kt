package com.github.ethanhosier.analysis.divergence

import com.github.ethanhosier.analysis.metrics.ck.CkResult
import com.github.ethanhosier.analysis.metrics.gitdiff.DiffStats
import com.github.ethanhosier.analysis.metrics.gradlebuild.BuildResult
import com.github.ethanhosier.analysis.metrics.model.AlternativeTrajectory
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.pipeline.CheckpointReport
import com.github.ethanhosier.analysis.pipeline.DerivedMetrics
import com.github.ethanhosier.analysis.pipeline.DivergenceKind
import com.github.ethanhosier.analysis.pipeline.ProcessScore
import com.github.ethanhosier.analysis.metrics.pmd.PmdResult
import com.github.ethanhosier.analysis.metrics.tests.TestResult
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DivergencePointBuilderTest {

    private fun stubMetrics(sha: String): CheckpointMetrics = CheckpointMetrics(
        sha = sha,
        ck = CkResult(perClass = emptyList()),
        pmd = PmdResult(violations = emptyList()),
        build = BuildResult(success = true, exitCode = 0, durationMs = 0, timedOut = false, stderrTail = ""),
        tests = TestResult(
            success = true, exitCode = 0, durationMs = 0, timedOut = false,
            total = 0, passed = 0, failed = 0, skipped = 0, failures = emptyList(),
            stderrTail = "",
        ),
    )

    private fun userCp(sha: String, process: Int): CheckpointReport =
        CheckpointReport(
            sha = sha,
            events = emptyList(),
            metrics = stubMetrics(sha),
            diff = DiffStats.ZERO,
            derivedMetrics = DerivedMetrics.EMPTY.copy(
                process = ProcessScore(
                    total = process,
                    baseline = 50,
                    clamped = false,
                    contributions = emptyList(),
                ),
            ),
        )

    private fun altCp(sha: String, process: Int): CheckpointReport =
        userCp(sha, process)

    private fun extractMethodSpec(): RefactoringSpec = RefactoringSpec.ExtractMethod(
        relativeFilePath = "Foo.java",
        declaringTypeFqn = "com.example.Foo",
        hostMethodName = "bar",
        hostMethodParamTypes = listOf("int"),
        selectionSubtreeHash = "h",
        selectionNodeCount = 1,
        newMethodName = "ex",
    )

    @Test
    fun `single-step IDE alt emits one IDE_REPLAY DP`() {
        val users = listOf(userCp("u0", 50), userCp("u1", 60))
        val alt = AlternativeTrajectory(
            kind = DivergenceKind.IDE_REPLAY,
            stepIndexes = listOf(0),
            fromSha = "u0",
            userToSha = "u1",
            branchRefs = listOf("br"),
            specs = listOf(extractMethodSpec()),
            altCheckpoints = listOf(altCp("a0", 75)),
        )

        val dps = DivergencePointBuilder.build(
            alts = listOf(alt),
            userCheckpoints = users,
        )

        assertEquals(1, dps.size)
        val dp = dps[0]
        assertEquals(DivergenceKind.IDE_REPLAY, dp.kind)
        assertEquals(0, dp.stepIndex)
        assertEquals(15.0, dp.magnitude)
        assertEquals(listOf(0), dp.altTrajectoryIndexes)
        assertEquals("ExtractMethod", dp.replacedRefactoringId)
        assertTrue(dp.title.contains("Extract Method"))
    }

    @Test
    fun `reorder window with two permutations collapses to one DP`() {
        val users = listOf(userCp("u0", 50), userCp("u1", 55), userCp("u2", 60))
        val perm1 = AlternativeTrajectory(
            kind = DivergenceKind.ORDERING,
            stepIndexes = listOf(1, 0),
            fromSha = "u0",
            userToSha = "u2",
            branchRefs = listOf("br1a", "br1b"),
            specs = listOf(extractMethodSpec(), extractMethodSpec()),
            altCheckpoints = listOf(altCp("p1a", 58), altCp("p1b", 72)),
        )
        val perm2 = AlternativeTrajectory(
            kind = DivergenceKind.ORDERING,
            stepIndexes = listOf(1, 0),
            fromSha = "u0",
            userToSha = "u2",
            branchRefs = listOf("br2a", "br2b"),
            specs = listOf(extractMethodSpec(), extractMethodSpec()),
            altCheckpoints = listOf(altCp("p2a", 57), altCp("p2b", 80)),
        )

        val dps = DivergencePointBuilder.build(
            alts = listOf(perm1, perm2),
            userCheckpoints = users,
        )

        assertEquals(1, dps.size)
        val dp = dps[0]
        assertEquals(DivergenceKind.ORDERING, dp.kind)
        assertEquals(listOf(0, 1), dp.altTrajectoryIndexes)
        assertEquals(20.0, dp.magnitude)
        assertEquals(listOf(0, 1), dp.orderingWindowSteps)
        assertEquals(1, dp.stepIndex)
    }

    @Test
    fun `rework alt emits REWORK DP with file and scope`() {
        val users = listOf(userCp("u0", 50), userCp("u1", 48), userCp("u2", 50))
        val alt = AlternativeTrajectory(
            kind = DivergenceKind.REWORK,
            stepIndexes = emptyList(),
            fromSha = "u0",
            userToSha = "u2",
            branchRefs = emptyList(),
            specs = emptyList(),
            altCheckpoints = listOf(altCp("u2", 50)),
        )
        val info = DivergencePointBuilder.ReworkInfo(
            originatingStepIndex = 1,
            terminalStepIndex = 2,
            file = "Foo.java",
            scopeLabel = "com.example.Foo#bar(int)",
            lineCount = 4,
            originatingPatch = "diff --git a/Foo.java b/Foo.java\n--- a/Foo.java\n+++ b/Foo.java\n@@ -10,0 +11,1 @@\n+x\n",
            terminalPatch = "diff --git a/Foo.java b/Foo.java\n--- a/Foo.java\n+++ b/Foo.java\n@@ -11,1 +10,0 @@\n-x\n",
            direction = "ADD_THEN_REMOVE",
        )

        val dps = DivergencePointBuilder.build(
            alts = listOf(alt),
            userCheckpoints = users,
            reworkInfoByAltIndex = mapOf(0 to info),
        )

        assertEquals(1, dps.size)
        val dp = dps[0]
        assertEquals(DivergenceKind.REWORK, dp.kind)
        assertEquals(1, dp.stepIndex)
        assertEquals(1, dp.originatingStepIndex)
        assertEquals("Foo.java", dp.file)
        assertEquals("com.example.Foo#bar(int)", dp.scopeLabel)
        assertEquals(4, dp.reworkLineCount)
        assertEquals(4.0, dp.magnitude)
        assertEquals(info.originatingPatch, dp.originatingPatch)
        assertEquals(info.terminalPatch, dp.terminalPatch)
    }

    @Test
    fun `IDE_REPLAY emits a DP regardless of magnitude — small positive delta`() {
        // The magnitude floor was removed from DivergencePointBuilder so that
        // detection / synthesis / surfacing are decoupled from suggestion-quality
        // at the tool layer. Downstream consumers apply their own threshold.
        val users = listOf(userCp("u0", 50), userCp("u1", 60))
        val alt = AlternativeTrajectory(
            kind = DivergenceKind.IDE_REPLAY,
            stepIndexes = listOf(0),
            fromSha = "u0",
            userToSha = "u1",
            branchRefs = listOf("br"),
            specs = listOf(extractMethodSpec()),
            altCheckpoints = listOf(altCp("a0", 61)),
        )

        val dps = DivergencePointBuilder.build(
            alts = listOf(alt),
            userCheckpoints = users,
        )

        assertEquals(1, dps.size)
        assertEquals(DivergenceKind.IDE_REPLAY, dps[0].kind)
        assertEquals(1.0, dps[0].magnitude)
    }

    @Test
    fun `IDE_REPLAY emits a DP with negative magnitude when alt scores worse`() {
        // Surfacing now also includes alts that scored *worse* than the user,
        // so the experiment can analyse the full distribution post-hoc.
        val users = listOf(userCp("u0", 50), userCp("u1", 80))
        val alt = AlternativeTrajectory(
            kind = DivergenceKind.IDE_REPLAY,
            stepIndexes = listOf(0),
            fromSha = "u0",
            userToSha = "u1",
            branchRefs = listOf("br"),
            specs = listOf(extractMethodSpec()),
            altCheckpoints = listOf(altCp("a0", 70)),
        )

        val dps = DivergencePointBuilder.build(
            alts = listOf(alt),
            userCheckpoints = users,
        )

        assertEquals(1, dps.size)
        assertEquals(-10.0, dps[0].magnitude)
    }

    @Test
    fun `REWORK below line threshold drops the DP`() {
        val users = listOf(userCp("u0", 50), userCp("u1", 50))
        val alt = AlternativeTrajectory(
            kind = DivergenceKind.REWORK,
            stepIndexes = emptyList(),
            fromSha = "u0",
            userToSha = "u1",
            branchRefs = emptyList(),
            specs = emptyList(),
            altCheckpoints = listOf(altCp("u1", 50)),
        )
        val info = DivergencePointBuilder.ReworkInfo(
            originatingStepIndex = 0,
            terminalStepIndex = 1,
            file = "Foo.java",
            scopeLabel = "Foo#bar()",
            lineCount = 1,
            originatingPatch = "",
            terminalPatch = "",
            direction = "ADD_THEN_REMOVE",
        )

        val dps = DivergencePointBuilder.build(
            alts = listOf(alt),
            userCheckpoints = users,
            reworkInfoByAltIndex = mapOf(0 to info),
        )

        assertTrue(dps.isEmpty())
    }
}
