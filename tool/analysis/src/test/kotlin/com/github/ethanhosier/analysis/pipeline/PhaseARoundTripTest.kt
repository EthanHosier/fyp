package com.github.ethanhosier.analysis.pipeline

import com.github.ethanhosier.analysis.alternative.IdeRefactoringsRunner
import com.github.ethanhosier.analysis.diffs.DiffsRunner
import com.github.ethanhosier.analysis.metrics.MetricsRunner
import com.github.ethanhosier.analysis.metrics.ck.CkResult
import com.github.ethanhosier.analysis.metrics.cpd.CpdTrackingRunner
import com.github.ethanhosier.analysis.metrics.derived.ScoringConfig
import com.github.ethanhosier.analysis.metrics.gradlebuild.BuildResult
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.metrics.model.ReorderOrdering
import com.github.ethanhosier.analysis.metrics.model.ReorderTrajectory
import com.github.ethanhosier.analysis.metrics.model.ResidualSummary
import com.github.ethanhosier.analysis.metrics.pmd.PmdResult
import com.github.ethanhosier.analysis.metrics.pmd.PmdTrackingRunner
import com.github.ethanhosier.analysis.metrics.tests.TestResult
import com.github.ethanhosier.analysis.miner.RefactoringMinerRunner
import com.github.ethanhosier.analysis.miner.model.DetectedRefactoring
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.model.Trace
import com.github.ethanhosier.analysis.reconstruct.EventCommitMap
import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.SessionMetadata
import com.github.ethanhosier.ideplugin.model.TraceEvent
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phase 1.3 round-trip guarantee: encoding a [PhaseAResult] to JSON and
 * decoding it back must produce an [AnalysisReport] (via
 * [ReportAssembler.assemble]) that equals the in-process report.
 *
 * The fixture is built directly from the same in-memory helpers
 * `AnalysisPipelineTest` uses — full end-to-end fixture sessions live in
 * `:analysis:run` smoke runs, not in unit tests.
 */
class PhaseARoundTripTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    @Test
    fun `PhaseAResult round-trips through JSON and assembles to the same report`() {
        val phaseA = buildFixturePhaseA()

        val encoded = json.encodeToString(PhaseAResult.serializer(), phaseA)
        val decoded = json.decodeFromString(PhaseAResult.serializer(), encoded)

        val inProcessReport = ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)
        val roundTrippedReport = ReportAssembler.assemble(decoded, ScoringConfig.PRODUCTION)

        // `RunInfo.generatedAt` is `System.currentTimeMillis()` at assemble
        // time — intrinsically lossy across two separate assembly calls.
        // Everything else (scored metrics, divergence points, checkpoint
        // shapes) is deterministic from Phase A + config, so we normalise
        // that one field before structural comparison.
        assertEquals(normalised(inProcessReport), normalised(roundTrippedReport))
    }

    @Test
    fun `assemble is deterministic on the same PhaseAResult`() {
        val phaseA = buildReorderPhaseA()
        val a = ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)
        val b = ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)
        assertEquals(
            json.encodeToString(AnalysisReport.serializer(), normalised(a)),
            json.encodeToString(AnalysisReport.serializer(), normalised(b)),
        )
    }

    @Test
    fun `PhaseAResult with reorder trajectory round-trips`() {
        val phaseA = buildReorderPhaseA()

        val encoded = json.encodeToString(PhaseAResult.serializer(), phaseA)
        val decoded = json.decodeFromString(PhaseAResult.serializer(), encoded)

        val inProcessReport = ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)
        val roundTrippedReport = ReportAssembler.assemble(decoded, ScoringConfig.PRODUCTION)

        // Compare via JSON encoding so the assertion diff is searchable
        // (data-class equality on this graph produces a multi-megabyte
        // toString that is impractical to debug from.)
        val inProcessJson = json.encodeToString(
            AnalysisReport.serializer(),
            normalised(inProcessReport),
        )
        val roundTrippedJson = json.encodeToString(
            AnalysisReport.serializer(),
            normalised(roundTrippedReport),
        )
        assertEquals(inProcessJson, roundTrippedJson)
    }

    private fun normalised(report: AnalysisReport): AnalysisReport =
        report.copy(run = report.run.copy(generatedAt = 0L))

    private fun buildFixturePhaseA(): PhaseAResult {
        val trace = Trace(
            metadata = metadata("sess-rt-1"),
            events = listOf(
                event("e1", EventType.SESSION_STARTED, timestamp = 100),
                event("e2", EventType.EDIT_BURST, timestamp = 200),
                event("e3", EventType.EDIT_BURST, timestamp = 300),
            ),
        )
        val reconstruction = ReconstructionResult(
            repoDir = Path.of("/tmp/fake-rt-1"),
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
        return PhaseAResult(
            trace = trace,
            reconstruction = reconstruction,
            metrics = metrics,
            miner = RefactoringMinerRunner.Summary(checkpointsAnalysed = 2, steps = emptyList()),
            alternative = IdeRefactoringsRunner.Summary(
                candidates = 0,
                synthesised = emptyList(),
                skipped = emptyMap(),
            ),
            diffs = DiffsRunner.Summary(
                checkpointPatches = mapOf("sha-a" to "", "sha-b" to "diff --git a/x b/x"),
                refactoringPatches = emptyMap(),
            ),
            trackedCodeSmells = PmdTrackingRunner.Summary(
                trackingBySha = emptyMap(),
                alternativeTrackingBySha = emptyMap(),
            ),
            trackedDuplication = CpdTrackingRunner.Summary(
                trackingBySha = emptyMap(),
                alternativeTrackingBySha = emptyMap(),
            ),
            parallelism = 4,
            metricsDurationMs = 1_234,
            reorderTrajectories = emptyList(),
            augmentedAltMetricsBySha = null,
            reworkSummary = null,
        )
    }

    private fun buildReorderPhaseA(): PhaseAResult {
        val trace = Trace(metadata = metadata("sess-rt-2"), events = emptyList())
        val reconstruction = ReconstructionResult(
            repoDir = Path.of("/tmp/fake-rt-2"),
            eventCommits = EventCommitMap(
                mapping = linkedMapOf("e1" to "from", "e2" to "to"),
            ),
        )
        val fromMetrics = checkpoint("from")
        val toMetrics = checkpoint("to")
        val intermediate = checkpoint("ord-s0")
        val terminalAlias = toMetrics.copy(sha = "ord-s1-terminal")
        val metrics = MetricsRunner.Summary(
            totalShas = 2,
            computed = 3,
            buildOk = 3,
            testsOk = 3,
            checkpoints = listOf(fromMetrics, toMetrics),
            alternativeCheckpoints = listOf(intermediate),
        )
        val augmentedAltMetricsBySha = mapOf(
            "ord-s0" to intermediate,
            "ord-s1-terminal" to terminalAlias,
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
        val miner = RefactoringMinerRunner.Summary(
            checkpointsAnalysed = 2,
            steps = listOf(refactoringStep(0), refactoringStep(1)),
        )
        val residual = ResidualSummary(
            applied = true,
            addedLines = 0,
            deletedLines = 0,
            rejectedFiles = emptyList(),
        )
        // Include an IDE-refactoring synthesis so the SynthesisedGroup
        // arm of the JSON form is also exercised.
        val alternative = IdeRefactoringsRunner.Summary(
            candidates = 0,
            synthesised = emptyList(),
            skipped = mapOf(0 to "test-only fixture"),
        )

        return PhaseAResult(
            trace = trace,
            reconstruction = reconstruction,
            metrics = metrics,
            miner = miner,
            alternative = alternative,
            diffs = DiffsRunner.Summary(
                checkpointPatches = emptyMap(),
                refactoringPatches = emptyMap(),
                alternativePatches = mapOf("ord-s0" to "", "ord-s1-terminal" to ""),
            ),
            trackedCodeSmells = PmdTrackingRunner.Summary(
                trackingBySha = emptyMap(),
                alternativeTrackingBySha = emptyMap(),
            ),
            trackedDuplication = CpdTrackingRunner.Summary(
                trackingBySha = emptyMap(),
                alternativeTrackingBySha = emptyMap(),
            ),
            parallelism = 1,
            metricsDurationMs = 0,
            reorderTrajectories = listOf(traj),
            augmentedAltMetricsBySha = augmentedAltMetricsBySha,
            reworkSummary = null,
            specsByStepIndex = miner.steps
                .mapNotNull { s -> s.spec?.let { s.stepIndex to it } }
                .toMap(),
        )
    }

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
        sessionId = "sess-rt-1",
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
