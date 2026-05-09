package com.github.ethanhosier.analysis.pipeline

import com.github.ethanhosier.analysis.alternative.AlternativeTrajectoryRunner
import com.github.ethanhosier.analysis.metrics.MetricsRunner
import com.github.ethanhosier.analysis.metrics.ck.CkResult
import com.github.ethanhosier.analysis.metrics.gradlebuild.BuildResult
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.metrics.model.ReorderOrdering
import com.github.ethanhosier.analysis.metrics.model.ReorderTrajectory
import com.github.ethanhosier.analysis.metrics.pmd.PmdResult
import com.github.ethanhosier.analysis.metrics.pmd.PmdTrackingRunner
import com.github.ethanhosier.analysis.metrics.tests.TestResult
import com.github.ethanhosier.analysis.diffs.DiffsRunner
import com.github.ethanhosier.analysis.miner.RefactoringMinerRunner
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.model.Trace
import com.github.ethanhosier.analysis.reconstruct.EventCommitMap
import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.SessionMetadata
import com.github.ethanhosier.ideplugin.model.TraceEvent
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Unit tests for the pure in-memory report assembly — no I/O, no Gradle.
 * The full pipeline (`AnalysisPipeline.run`) is covered end-to-end by the
 * CLI regression; [MetricsRunnerTest] already exercises the expensive
 * per-SHA runners.
 */
class AnalysisPipelineTest {

    @Test
    fun `groups events under the SHAs they landed on, preserving order`() {
        // Three events across two unique SHAs:
        //   e1 (SESSION_STARTED) → sha-a
        //   e2 (EDIT_BURST)      → sha-a (collapsed — same SHA as preceding)
        //   e3 (EDIT_BURST)      → sha-b
        val trace = Trace(
            metadata = metadata("sess-1"),
            events = listOf(
                event("e1", EventType.SESSION_STARTED, timestamp = 100),
                event("e2", EventType.EDIT_BURST, timestamp = 200),
                event("e3", EventType.EDIT_BURST, timestamp = 300),
            ),
        )
        val reconstruction = ReconstructionResult(
            repoDir = Path.of("/tmp/fake"),
            eventCommits = EventCommitMap(
                mapping = linkedMapOf("e1" to "sha-a", "e2" to "sha-a", "e3" to "sha-b"),
            ),
        )
        val metrics = MetricsRunner.Summary(
            totalShas = 2,
            computed = 2,
            buildOk = 2,
            testsOk = 1,
            checkpoints = listOf(checkpoint("sha-a"), checkpoint("sha-b")),
        )

        val report = buildAnalysisReport(
            trace = trace,
            reconstruction = reconstruction,
            metrics = metrics,
            miner = emptyMinerSummary(),
            alternative = emptyAlternativeSummary(),
            diffs = emptyDiffsSummary(),
            pmdTracking = emptyPmdTrackingSummary(),
            parallelism = 4,
            metricsDurationMs = 12_345,
        )

        assertEquals("sess-1", report.session.sessionId)
        assertEquals(4, report.run.parallelism)
        assertEquals(12_345, report.run.metricsDurationMs)

        assertEquals(listOf("sha-a", "sha-b"), report.checkpoints.map { it.sha })

        val shaA = report.checkpoints[0]
        assertEquals(listOf("e1", "e2"), shaA.events.map { it.id })
        assertEquals(listOf(100L, 200L), shaA.events.map { it.timestamp })

        val shaB = report.checkpoints[1]
        assertEquals(listOf("e3"), shaB.events.map { it.id })

        assertEquals(emptyList(), report.refactoringSteps)
    }

    @Test
    fun `checkpoint with no mapped events still appears in report`() {
        // Pathological but possible: MetricsRunner computed a SHA for which
        // no event directly maps (e.g. the baseline commit, if we ever map
        // no event to it). The SHA must still surface in the report — with
        // an empty event list — so the metrics aren't silently dropped.
        val trace = Trace(metadata = metadata("sess-2"), events = emptyList())
        val reconstruction = ReconstructionResult(
            repoDir = Path.of("/tmp/fake"),
            eventCommits = EventCommitMap(mapping = emptyMap()),
        )
        val metrics = MetricsRunner.Summary(
            totalShas = 1,
            computed = 1,
            buildOk = 1,
            testsOk = 1,
            checkpoints = listOf(checkpoint("sha-orphan")),
        )

        val report = buildAnalysisReport(
            trace = trace,
            reconstruction = reconstruction,
            metrics = metrics,
            miner = emptyMinerSummary(),
            alternative = emptyAlternativeSummary(),
            diffs = emptyDiffsSummary(),
            pmdTracking = emptyPmdTrackingSummary(),
            parallelism = 1,
            metricsDurationMs = 0,
        )

        assertEquals(1, report.checkpoints.size)
        assertEquals("sha-orphan", report.checkpoints[0].sha)
        assertEquals(emptyList(), report.checkpoints[0].events)
    }

    @Test
    fun `successful reorder ordering aliases terminal step to windowToSha checkpoint`() {
        val trace = Trace(metadata = metadata("sess-r1"), events = emptyList())
        val reconstruction = ReconstructionResult(
            repoDir = Path.of("/tmp/fake"),
            eventCommits = EventCommitMap(
                mapping = linkedMapOf("e1" to "from", "e2" to "to"),
            ),
        )
        val fromCp = checkpoint("from")
        val toCp = checkpoint("to")
        val intermediateCp = checkpoint("ord-s0")
        val metrics = MetricsRunner.Summary(
            totalShas = 2,
            computed = 3,
            buildOk = 3,
            testsOk = 3,
            checkpoints = listOf(fromCp, toCp),
            alternativeCheckpoints = listOf(intermediateCp),
        )
        val ord = ReorderOrdering(
            orderIndex = 0,
            permutation = listOf(1, 0),
            permutationLabels = listOf("B", "A"),
            stepShas = listOf("ord-s0", "ord-s1-terminal"),
            branchRefs = listOf("reorder/win0/0", "reorder/win0/0-1"),
            terminalSuccess = true,
        )
        val traj = ReorderTrajectory(
            windowIndex = 0,
            windowFromSha = "from",
            windowToSha = "to",
            windowStepIndexes = listOf(0, 1),
            windowSpecLabels = listOf("A", "B"),
            orderings = listOf(ord),
        )

        val report = buildAnalysisReport(
            trace = trace,
            reconstruction = reconstruction,
            metrics = metrics,
            miner = emptyMinerSummary(),
            alternative = emptyAlternativeSummary(),
            diffs = emptyDiffsSummary(),
            pmdTracking = emptyPmdTrackingSummary(),
            parallelism = 1,
            metricsDurationMs = 0,
            reorderTrajectories = listOf(traj),
        )

        val scoredOrd = report.reorderTrajectories.single().orderings.single()
        assertEquals(2, scoredOrd.metrics.size)
        // Intermediate step → alt checkpoint by SHA.
        assertSame(intermediateCp, scoredOrd.metrics[0])
        // Terminal step → user windowToSha checkpoint by reference (no rebuild).
        assertSame(toCp, scoredOrd.metrics[1])
    }

    @Test
    fun `failed reorder ordering keeps empty metrics`() {
        val trace = Trace(metadata = metadata("sess-r2"), events = emptyList())
        val reconstruction = ReconstructionResult(
            repoDir = Path.of("/tmp/fake"),
            eventCommits = EventCommitMap(
                mapping = linkedMapOf("e1" to "from", "e2" to "to"),
            ),
        )
        val metrics = MetricsRunner.Summary(
            totalShas = 2,
            computed = 2,
            buildOk = 2,
            testsOk = 2,
            checkpoints = listOf(checkpoint("from"), checkpoint("to")),
        )
        val ord = ReorderOrdering(
            orderIndex = 0,
            permutation = listOf(1, 0),
            permutationLabels = listOf("B", "A"),
            stepShas = listOf("ord-s0"),
            branchRefs = listOf("reorder/win0/0"),
            terminalSuccess = false,
            failedAt = 1,
        )
        val traj = ReorderTrajectory(
            windowIndex = 0,
            windowFromSha = "from",
            windowToSha = "to",
            windowStepIndexes = listOf(0, 1),
            windowSpecLabels = listOf("A", "B"),
            orderings = listOf(ord),
        )

        val report = buildAnalysisReport(
            trace = trace,
            reconstruction = reconstruction,
            metrics = metrics,
            miner = emptyMinerSummary(),
            alternative = emptyAlternativeSummary(),
            diffs = emptyDiffsSummary(),
            pmdTracking = emptyPmdTrackingSummary(),
            parallelism = 1,
            metricsDurationMs = 0,
            reorderTrajectories = listOf(traj),
        )

        val scoredOrd = report.reorderTrajectories.single().orderings.single()
        assertTrue(scoredOrd.metrics.isEmpty())
    }

    private fun emptyMinerSummary() = RefactoringMinerRunner.Summary(
        checkpointsAnalysed = 0,
        steps = emptyList(),
    )

    private fun emptyAlternativeSummary() = AlternativeTrajectoryRunner.Summary(
        candidates = 0,
        synthesised = emptyList(),
        skipped = emptyMap(),
    )

    private fun emptyDiffsSummary() = DiffsRunner.Summary(
        checkpointPatches = emptyMap(),
        refactoringPatches = emptyMap(),
    )

    private fun emptyPmdTrackingSummary() = PmdTrackingRunner.Summary(
        trackingBySha = emptyMap(),
        alternativeTrackingBySha = emptyMap(),
    )

    private fun metadata(id: String) = SessionMetadata(
        sessionId = id,
        name = "test",
        projectName = "proj",
        projectPath = "/tmp/proj",
        startTime = 0,
        endTime = 1,
        ideVersion = "ij",
        pluginVersion = "pv",
    )

    private fun event(id: String, type: EventType, timestamp: Long) = TraceEvent(
        id = id,
        type = type,
        timestamp = timestamp,
        sessionId = "sess-1",
    )

    private fun checkpoint(sha: String) = CheckpointMetrics(
        sha = sha,
        ck = CkResult(perClass = emptyList()),
        pmd = PmdResult(violations = emptyList()),
        build = BuildResult(
            success = true,
            exitCode = 0,
            durationMs = 0,
            timedOut = false,
            stderrTail = "",
        ),
        tests = TestResult(
            success = true,
            exitCode = 0,
            durationMs = 0,
            timedOut = false,
            total = 0,
            passed = 0,
            failed = 0,
            skipped = 0,
            failures = emptyList(),
            stderrTail = "",
        ),
    )
}
