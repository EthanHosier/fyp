package com.github.ethanhosier.analysis.pipeline

import com.github.ethanhosier.analysis.alternative.AlternativeTrajectoryRunner
import com.github.ethanhosier.analysis.metrics.MetricsRunner
import com.github.ethanhosier.analysis.metrics.ck.CkResult
import com.github.ethanhosier.analysis.metrics.gradlebuild.BuildResult
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.metrics.model.ReorderOrdering
import com.github.ethanhosier.analysis.metrics.model.ReorderTrajectory
import com.github.ethanhosier.analysis.metrics.model.ResidualSummary
import com.github.ethanhosier.analysis.miner.model.DetectedRefactoring
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
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
    fun `successful reorder ordering becomes a multi-step AlternativeTrajectory with aliased terminal`() {
        val trace = Trace(metadata = metadata("sess-r1"), events = emptyList())
        val reconstruction = ReconstructionResult(
            repoDir = Path.of("/tmp/fake"),
            eventCommits = EventCommitMap(
                mapping = linkedMapOf("e1" to "from", "e2" to "to"),
            ),
        )
        val fromMetrics = checkpoint("from")
        val toMetrics = checkpoint("to")
        val intermediateMetrics = checkpoint("ord-s0")
        // Synthetic terminal alias — same content as `toMetrics` (slice 2b)
        // but rebranded with the alt's terminal sha. In real pipelines this
        // is built upstream; tests pass it via `augmentedAltMetricsBySha`.
        val terminalAliasMetrics = toMetrics.copy(sha = "ord-s1-terminal")
        val metrics = MetricsRunner.Summary(
            totalShas = 2,
            computed = 3,
            buildOk = 3,
            testsOk = 3,
            checkpoints = listOf(fromMetrics, toMetrics),
            alternativeCheckpoints = listOf(intermediateMetrics),
        )
        val augmentedAltMetricsBySha = mapOf(
            "ord-s0" to intermediateMetrics,
            "ord-s1-terminal" to terminalAliasMetrics,
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
        // miner stub providing typed specs for both window step indexes.
        val stepA = refactoringStep(stepIndex = 0)
        val stepB = refactoringStep(stepIndex = 1)
        val miner = RefactoringMinerRunner.Summary(checkpointsAnalysed = 2, steps = listOf(stepA, stepB))

        val report = buildAnalysisReport(
            trace = trace,
            reconstruction = reconstruction,
            metrics = metrics,
            miner = miner,
            alternative = emptyAlternativeSummary(),
            diffs = emptyDiffsSummary(),
            pmdTracking = emptyPmdTrackingSummary(),
            parallelism = 1,
            metricsDurationMs = 0,
            reorderTrajectories = listOf(traj),
            augmentedAltMetricsBySha = augmentedAltMetricsBySha,
        )

        val alts = report.alternativeTrajectories
        assertEquals(1, alts.size)
        val alt = alts.single()
        assertEquals(listOf(1, 0), alt.stepIndexes)         // permutation order
        assertEquals("from", alt.fromSha)
        assertEquals("to", alt.userToSha)
        assertEquals(listOf("ord-s0", "ord-s1-terminal"), alt.altCheckpoints.map { it.sha })
        // Terminal step's CheckpointMetrics aliases the user's windowToSha
        // checkpoint metrics (Slice 2b). Same instance, just rebranded sha.
        assertSame(terminalAliasMetrics, alt.altCheckpoints.last().metrics)
        // ReorderOrdering itself no longer carries a `metrics` field
        // (Slice 2c removed it); per-step data lives on AlternativeTrajectory.
        assertEquals(1, report.reorderTrajectories.single().orderings.size)
    }

    @Test
    fun `synthesised group expands per-refactoring + residual into AlternativeTrajectory chain`() {
        // Two refactorings on the same (fromSha, toSha) window with a
        // residual that landed cleanly should produce one
        // AlternativeTrajectory whose altCheckpoints chain is
        // [refactor1, refactor2, residual] — specs.size == 2 and
        // altCheckpoints.size == specs.size + 1 to indicate the trailing
        // residual step.
        val trace = Trace(metadata = metadata("sess-g1"), events = emptyList())
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
        val refactor1Metrics = checkpoint("group-step-0")
        val refactor2Metrics = checkpoint("group-step-1")
        val residualMetrics = checkpoint("group-residual")
        val augmented = mapOf(
            "group-step-0" to refactor1Metrics,
            "group-step-1" to refactor2Metrics,
            "group-residual" to residualMetrics,
        )

        // Both steps share fromSha + toSha — co-located refactoring group.
        val stepA = refactoringStep(stepIndex = 0).copy(fromSha = "from", toSha = "to")
        val stepB = refactoringStep(stepIndex = 1).copy(fromSha = "from", toSha = "to")
        val miner = RefactoringMinerRunner.Summary(
            checkpointsAnalysed = 2,
            steps = listOf(stepA, stepB),
        )
        val residual = ResidualSummary(
            applied = true,
            addedLines = 3,
            deletedLines = 1,
            rejectedFiles = emptyList(),
        )
        val alternative = AlternativeTrajectoryRunner.Summary(
            candidates = 1,
            synthesised = listOf(
                AlternativeTrajectoryRunner.SynthesisedGroup(
                    stepIndexes = listOf(0, 1),
                    fromSha = "from",
                    userToSha = "to",
                    altShas = listOf("group-step-0", "group-step-1", "group-residual"),
                    branchRefs = listOf(
                        "alt/group-0/0",
                        "alt/group-0/1",
                        "alt/group-0/residual",
                    ),
                    residual = residual,
                ),
            ),
            skipped = emptyMap(),
        )

        val report = buildAnalysisReport(
            trace = trace,
            reconstruction = reconstruction,
            metrics = metrics,
            miner = miner,
            alternative = alternative,
            diffs = emptyDiffsSummary(),
            pmdTracking = emptyPmdTrackingSummary(),
            parallelism = 1,
            metricsDurationMs = 0,
            augmentedAltMetricsBySha = augmented,
        )

        val alts = report.alternativeTrajectories
        assertEquals(1, alts.size)
        val alt = alts.single()
        assertEquals(listOf(0, 1), alt.stepIndexes)
        assertEquals("from", alt.fromSha)
        assertEquals("to", alt.userToSha)
        assertEquals(2, alt.specs.size)
        // altCheckpoints chain: two refactorings + residual cleanup.
        assertEquals(
            listOf("group-step-0", "group-step-1", "group-residual"),
            alt.altCheckpoints.map { it.sha },
        )
        assertEquals(residual, alt.residual)
    }

    @Test
    fun `alt with non-trace-end userToSha gets a continuation chain`() {
        // 3 user checkpoints: from → mid → end. Alt covers from → mid,
        // so its userToSha is "mid" (not the trace end). The pipeline
        // should populate continuationCheckpointShas with the user's
        // post-merge SHAs (["end"]) and a matching continuation score.
        val trace = Trace(metadata = metadata("sess-cont"), events = emptyList())
        val reconstruction = ReconstructionResult(
            repoDir = Path.of("/tmp/fake"),
            eventCommits = EventCommitMap(
                mapping = linkedMapOf("e1" to "from", "e2" to "mid", "e3" to "end"),
            ),
        )
        val metrics = MetricsRunner.Summary(
            totalShas = 3,
            computed = 3,
            buildOk = 3,
            testsOk = 3,
            checkpoints = listOf(checkpoint("from"), checkpoint("mid"), checkpoint("end")),
        )
        val altMetrics = checkpoint("alt-mid")
        val augmented = mapOf("alt-mid" to altMetrics)

        val step = refactoringStep(stepIndex = 0).copy(fromSha = "from", toSha = "mid")
        val miner = RefactoringMinerRunner.Summary(
            checkpointsAnalysed = 3,
            steps = listOf(step),
        )
        val alternative = AlternativeTrajectoryRunner.Summary(
            candidates = 1,
            synthesised = listOf(
                AlternativeTrajectoryRunner.SynthesisedGroup(
                    stepIndexes = listOf(0),
                    fromSha = "from",
                    userToSha = "mid",
                    altShas = listOf("alt-mid"),
                    branchRefs = listOf("alt/group-0/0"),
                    residual = ResidualSummary(applied = true, addedLines = 0, deletedLines = 0, rejectedFiles = emptyList()),
                ),
            ),
            skipped = emptyMap(),
        )

        val report = buildAnalysisReport(
            trace = trace,
            reconstruction = reconstruction,
            metrics = metrics,
            miner = miner,
            alternative = alternative,
            diffs = emptyDiffsSummary(),
            pmdTracking = emptyPmdTrackingSummary(),
            parallelism = 1,
            metricsDurationMs = 0,
            augmentedAltMetricsBySha = augmented,
        )

        val alt = report.alternativeTrajectories.single()
        assertEquals(listOf("end"), alt.continuationCheckpoints.map { it.sha })
        // Static metrics carry forward from the user's checkpoint; the
        // overlay swaps in the alt's recomputed process score, so each
        // continuation entry has a non-empty `process`.
        assertTrue(alt.continuationCheckpoints.all { it.derivedMetrics.process.total in 0..100 })
    }

    @Test
    fun `failed reorder ordering emits no AlternativeTrajectory entry`() {
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
            miner = RefactoringMinerRunner.Summary(
                checkpointsAnalysed = 2,
                steps = listOf(refactoringStep(0), refactoringStep(1)),
            ),
            alternative = emptyAlternativeSummary(),
            diffs = emptyDiffsSummary(),
            pmdTracking = emptyPmdTrackingSummary(),
            parallelism = 1,
            metricsDurationMs = 0,
            reorderTrajectories = listOf(traj),
        )

        // Failed orderings produce no AlternativeTrajectory entries — the
        // reorder window data still appears for diagnostics.
        assertTrue(report.alternativeTrajectories.isEmpty())
        assertEquals(1, report.reorderTrajectories.single().orderings.size)
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

    private fun refactoringStep(stepIndex: Int) = RefactoringStep(
        stepIndex = stepIndex,
        fromSha = "sha-from-$stepIndex",
        toSha = "sha-to-$stepIndex",
        toCheckpointIndex = stepIndex,
        timestamp = 0L,
        refactoring = DetectedRefactoring(
            type = "Other",
            description = "step #$stepIndex",
            leftSideLocations = emptyList(),
            rightSideLocations = emptyList(),
            ideRelevant = false,
        ),
        spec = RefactoringSpec.Other,
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
