package com.github.ethanhosier.analysis.divergence

import com.github.ethanhosier.analysis.metrics.ck.CkResult
import com.github.ethanhosier.analysis.metrics.gitdiff.DiffStats
import com.github.ethanhosier.analysis.metrics.gradlebuild.BuildResult
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.metrics.model.CheckpointReport
import com.github.ethanhosier.analysis.metrics.pmd.PmdResult
import com.github.ethanhosier.analysis.metrics.tests.TestResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HygieneDetectorTest {

    private fun cp(
        sha: String,
        testsSkipped: Boolean = false,
        isCommit: Boolean = false,
    ): CheckpointReport = CheckpointReport(
        sha = sha,
        events = emptyList(),
        metrics = CheckpointMetrics(
            sha = sha,
            ck = CkResult(perClass = emptyList()),
            pmd = PmdResult(violations = emptyList()),
            build = BuildResult(success = true, exitCode = 0, durationMs = 0, timedOut = false, stderrTail = ""),
            tests = TestResult(
                success = !testsSkipped,
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

    @Test
    fun `TESTS_SKIPPED fires once per skipped-test checkpoint`() {
        val checkpoints = listOf(
            cp("u0"),
            cp("u1", testsSkipped = true),
            cp("u2"),
        )
        val findings = HygieneDetector.detect(checkpoints)
        assertEquals(1, findings.size)
        assertEquals(HygieneDetector.SubKind.TESTS_SKIPPED, findings[0].subKind)
        assertEquals(1, findings[0].anchorIndex)
    }

    @Test
    fun `TESTS_SKIPPED silent when every checkpoint ran tests`() {
        val checkpoints = listOf(cp("u0"), cp("u1"), cp("u2"))
        val findings = HygieneDetector.detect(checkpoints)
        assertTrue(
            findings.none { it.subKind == HygieneDetector.SubKind.TESTS_SKIPPED },
            "expected no TESTS_SKIPPED findings, got $findings",
        )
    }

    @Test
    fun `COMMIT_GAP fires every MIN_COMMIT_GAP checkpoints with no commits`() {
        // 14 checkpoints, zero commits. With MIN_COMMIT_GAP=6 and
        // lastCommitIdx=-1, the first fire happens at i=5 (gap=6), then
        // the walker treats that as a synthetic commit and fires again
        // at i=11 (gap=6).
        val checkpoints = (0 until 14).map { cp("u$it") }
        val findings = HygieneDetector
            .detect(checkpoints)
            .filter { it.subKind == HygieneDetector.SubKind.COMMIT_GAP }
        assertEquals(2, findings.size)
        assertEquals(listOf(5, 11), findings.map { it.anchorIndex })
        assertEquals(listOf(6, 6), findings.map { it.gapLength })
    }

    @Test
    fun `COMMIT_GAP silent on short trajectories with no commits`() {
        val checkpoints = (0 until 5).map { cp("u$it") }
        val findings = HygieneDetector
            .detect(checkpoints)
            .filter { it.subKind == HygieneDetector.SubKind.COMMIT_GAP }
        assertTrue(findings.isEmpty(), "expected no COMMIT_GAP findings, got $findings")
    }

    @Test
    fun `COMMIT_GAP clock resets at a real user commit`() {
        // 12 checkpoints, one real commit at i=4. Gap from -1 to 4 is 5
        // (< MIN_COMMIT_GAP); after the commit the next fire is at i=10
        // (gap=6 from i=4).
        val checkpoints = (0 until 12).map { idx -> cp("u$idx", isCommit = idx == 4) }
        val findings = HygieneDetector
            .detect(checkpoints)
            .filter { it.subKind == HygieneDetector.SubKind.COMMIT_GAP }
        assertEquals(1, findings.size)
        assertEquals(10, findings[0].anchorIndex)
        assertEquals(6, findings[0].gapLength)
    }
}
