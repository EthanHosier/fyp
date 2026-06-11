package com.github.ethanhosier.analysis.divergence

import com.github.ethanhosier.analysis.metrics.ck.CkResult
import com.github.ethanhosier.analysis.metrics.gitdiff.DiffStats
import com.github.ethanhosier.analysis.metrics.gradlebuild.BuildResult
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.pipeline.CheckpointReport
import com.github.ethanhosier.analysis.pipeline.EventSummary
import com.github.ethanhosier.analysis.metrics.pmd.PmdResult
import com.github.ethanhosier.analysis.metrics.tests.TestResult
import com.github.ethanhosier.analysis.miner.model.DetectedRefactoring
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.ideplugin.model.EventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HygieneDetectorTest {

    private fun cp(
        sha: String,
        testsSkipped: Boolean = false,
        isCommit: Boolean = false,
        ranTests: Boolean = false,
        buildOk: Boolean = true,
        testsOk: Boolean = true,
        testTs: Long? = null,
    ): CheckpointReport = CheckpointReport(
        sha = sha,
        events = if (ranTests) listOf(
            EventSummary(
                id = "$sha-test",
                type = EventType.TEST_RUN_FINISHED,
                timestamp = testTs ?: (sha.removePrefix("u").toLongOrNull() ?: 0L) * 120_000L,
            ),
        ) else emptyList(),
        metrics = CheckpointMetrics(
            sha = sha,
            ck = CkResult(perClass = emptyList()),
            pmd = PmdResult(violations = emptyList()),
            build = BuildResult(success = buildOk, exitCode = 0, durationMs = 0, timedOut = false, stderrTail = ""),
            tests = TestResult(
                success = testsOk && !testsSkipped,
                exitCode = 0,
                durationMs = 0,
                timedOut = false,
                wasSkipped = testsSkipped,
                total = 0, passed = 0, failed = 0, skipped = 0,
                failures = emptyList(), stderrTail = "",
            ),
        ),
        isUserCommit = isCommit,
        diff = DiffStats.ZERO,
    )

    private fun step(toIdx: Int, timestamp: Long = toIdx * 120_000L) = RefactoringStep(
        stepIndex = toIdx,
        fromSha = "u${toIdx - 1}",
        toSha = "u$toIdx",
        toCheckpointIndex = toIdx,
        timestamp = timestamp,
        refactoring = DetectedRefactoring(
            type = "Extract Method",
            description = "stub",
            leftSideLocations = emptyList(),
            rightSideLocations = emptyList(),
            ideRelevant = true,
        ),
        wasPerformedByIde = false,
    )

    @Test
    fun `TESTS_SKIPPED fires at refactoring steps whose checkpoint has no TEST_RUN_FINISHED`() {
        val checkpoints = listOf(
            cp("u0"),
            cp("u1"),                 // refactoring lands here, no test event
            cp("u2", ranTests = true), // refactoring lands here, test event present
        )
        val findings = HygieneDetector().detect(checkpoints, listOf(step(1), step(2)))
        assertEquals(1, findings.size)
        assertEquals(HygieneDetector.SubKind.TESTS_SKIPPED, findings[0].subKind)
        assertEquals(1, findings[0].anchorIndex)
    }

    @Test
    fun `TESTS_SKIPPED silent when every refactoring step ran tests`() {
        val checkpoints = listOf(
            cp("u0"),
            cp("u1", ranTests = true),
            cp("u2", ranTests = true),
        )
        val findings = HygieneDetector().detect(checkpoints, listOf(step(1), step(2)))
        assertTrue(
            findings.none { it.subKind == HygieneDetector.SubKind.TESTS_SKIPPED },
            "expected no TESTS_SKIPPED findings, got $findings",
        )
    }

    @Test
    fun `COMMIT_GAP fires every MIN_COMMIT_GAP green refactor checkpoints`() {
        val checkpoints = (0 until 14).map { cp("u$it") }
        val refactors = (0 until 14).map { step(it) }
        val findings = HygieneDetector()
            .detect(checkpoints, refactors)
            .filter { it.subKind == HygieneDetector.SubKind.COMMIT_GAP }
        assertEquals(2, findings.size)
        assertEquals(listOf(5, 11), findings.map { it.anchorIndex })
        assertEquals(listOf(6, 6), findings.map { it.gapLength })
    }

    @Test
    fun `COMMIT_GAP ignores non-refactor and non-green checkpoints`() {
        val checkpoints = (0 until 14).map { idx ->
            cp("u$idx", buildOk = idx != 5)
        }
        val refactors = (0 until 14).filter { it != 2 }.map { step(it) }
        val findings = HygieneDetector()
            .detect(checkpoints, refactors)
            .filter { it.subKind == HygieneDetector.SubKind.COMMIT_GAP }
        assertEquals(2, findings.size)
        assertEquals(listOf(7, 13), findings.map { it.anchorIndex })
    }

    @Test
    fun `COMMIT_GAP silent below threshold`() {
        // 5 green refactor checkpoints — short of the 6-step threshold.
        val checkpoints = (0 until 5).map { cp("u$it") }
        val findings = HygieneDetector()
            .detect(checkpoints, (0 until 5).map { step(it) })
            .filter { it.subKind == HygieneDetector.SubKind.COMMIT_GAP }
        assertTrue(findings.isEmpty(), "expected no COMMIT_GAP findings, got $findings")
    }

    @Test
    fun `COMMIT_GAP clock resets at a real user commit`() {
        val checkpoints = (0 until 12).map { idx -> cp("u$idx", isCommit = idx == 4) }
        val refactors = (0 until 12).map { step(it) }
        val findings = HygieneDetector()
            .detect(checkpoints, refactors)
            .filter { it.subKind == HygieneDetector.SubKind.COMMIT_GAP }
        assertEquals(1, findings.size)
        assertEquals(10, findings[0].anchorIndex)
        assertEquals(6, findings[0].gapLength)
    }
}
