package com.github.ethanhosier.analysis.advice

import com.github.ethanhosier.analysis.metrics.ck.CkResult
import com.github.ethanhosier.analysis.metrics.cpd.CpdResult
import com.github.ethanhosier.analysis.metrics.gradlebuild.BuildResult
import com.github.ethanhosier.analysis.metrics.model.AdviceKind
import com.github.ethanhosier.analysis.metrics.model.AdviceSeverity
import com.github.ethanhosier.analysis.metrics.model.AlternativeTrajectory
import com.github.ethanhosier.analysis.metrics.model.AnalysisReport
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.metrics.model.CheckpointReport
import com.github.ethanhosier.analysis.metrics.model.DerivedMetrics
import com.github.ethanhosier.analysis.metrics.model.PmdTracking
import com.github.ethanhosier.analysis.metrics.model.ProcessScore
import com.github.ethanhosier.analysis.metrics.model.UserGitCommit
import com.github.ethanhosier.analysis.metrics.pmd.PmdResult
import com.github.ethanhosier.analysis.metrics.readability.ReadabilityResult
import com.github.ethanhosier.analysis.metrics.tests.TestResult
import com.github.ethanhosier.analysis.miner.model.DetectedRefactoring
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.ideplugin.model.SessionMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrajectoryAdvisorTest {

    // ---- BUILD_OFTEN_BROKEN ----

    @Test
    fun `build often broken fires WARNING when share is between 15 and 30 percent`() {
        // 2 of 10 = 20% — warning band.
        val cps = (1..10).map { i ->
            cp("s$i", buildSuccess = i > 2)
        }
        val item = advise(cps).single { it.kind == AdviceKind.BUILD_OFTEN_BROKEN }
        assertEquals(AdviceSeverity.WARNING, item.severity)
        assertTrue("2/10" in item.body)
    }

    @Test
    fun `build often broken fires CRITICAL when share exceeds 30 percent`() {
        // 4 of 10 = 40%.
        val cps = (1..10).map { i -> cp("s$i", buildSuccess = i > 4) }
        val item = advise(cps).single { it.kind == AdviceKind.BUILD_OFTEN_BROKEN }
        assertEquals(AdviceSeverity.CRITICAL, item.severity)
    }

    @Test
    fun `build often broken does not fire when share is under threshold`() {
        // 1 of 10 = 10% — below 0.15.
        val cps = (1..10).map { i -> cp("s$i", buildSuccess = i != 5) }
        val items = advise(cps)
        assertNull(items.firstOrNull { it.kind == AdviceKind.BUILD_OFTEN_BROKEN })
    }

    @Test
    fun `build often broken skips trivially short sessions`() {
        val cps = listOf(cp("s1", buildSuccess = false), cp("s2", buildSuccess = false))
        val items = advise(cps)
        assertNull(items.firstOrNull { it.kind == AdviceKind.BUILD_OFTEN_BROKEN })
    }

    // ---- TEST_REGRESSIONS ----

    @Test
    fun `test regressions fires WARNING on a single green-to-red transition`() {
        val cps = listOf(
            cp("s1", testsSuccess = true),
            cp("s2", testsSuccess = false),
            cp("s3", testsSuccess = false),
        )
        val item = advise(cps).single { it.kind == AdviceKind.TEST_REGRESSIONS }
        assertEquals(AdviceSeverity.WARNING, item.severity)
        assertTrue("1 time" in item.body)
    }

    @Test
    fun `test regressions skips wasSkipped checkpoints when computing transitions`() {
        // Skipped checkpoints don't count as either pass or fail.
        val cps = listOf(
            cp("s1", testsSuccess = true),
            cp("s2", testsSkipped = true),
            cp("s3", testsSuccess = true),
        )
        assertNull(advise(cps).firstOrNull { it.kind == AdviceKind.TEST_REGRESSIONS })
    }

    @Test
    fun `test regressions fires CRITICAL at three or more regressions`() {
        val cps = listOf(
            cp("s1", testsSuccess = true), cp("s2", testsSuccess = false),
            cp("s3", testsSuccess = true), cp("s4", testsSuccess = false),
            cp("s5", testsSuccess = true), cp("s6", testsSuccess = false),
        )
        val item = advise(cps).single { it.kind == AdviceKind.TEST_REGRESSIONS }
        assertEquals(AdviceSeverity.CRITICAL, item.severity)
        assertTrue("3 times" in item.body)
    }

    @Test
    fun `test regressions does not fire on monotonically passing tests`() {
        val cps = (1..5).map { cp("s$it", testsSuccess = true) }
        assertNull(advise(cps).firstOrNull { it.kind == AdviceKind.TEST_REGRESSIONS })
    }

    // ---- LONG_STRETCH_WITHOUT_COMMIT ----

    @Test
    fun `long stretch without commit fires CRITICAL when no commits at all`() {
        val cps = (1..5).map { cp("s$it") }
        val item = advise(cps).single { it.kind == AdviceKind.LONG_STRETCH_WITHOUT_COMMIT }
        assertEquals(AdviceSeverity.CRITICAL, item.severity)
        assertTrue("never committed" in item.title.lowercase() || "never" in item.title)
    }

    @Test
    fun `long stretch without commit fires INFO at 6 to 11 checkpoints`() {
        // 7 checkpoints, no commits — `longest` = 7 → INFO.
        val cps = (1..7).map { cp("s$it") }
        val commit = userCommit("commitsha")
        val item = advise(cps, commits = listOf(commit))
            .single { it.kind == AdviceKind.LONG_STRETCH_WITHOUT_COMMIT }
        assertEquals(AdviceSeverity.INFO, item.severity)
        assertTrue("7 checkpoints" in item.body)
    }

    @Test
    fun `long stretch without commit fires WARNING at 12 or more`() {
        val cps = (1..14).map { cp("s$it") }
        val commit = userCommit("c")
        val item = advise(cps, commits = listOf(commit))
            .single { it.kind == AdviceKind.LONG_STRETCH_WITHOUT_COMMIT }
        assertEquals(AdviceSeverity.WARNING, item.severity)
    }

    @Test
    fun `long stretch without commit does not fire on short runs with commits`() {
        // Three checkpoints, with a commit landing on the second — longest run = 2.
        val cps = listOf(cp("s1"), cp("s2"), cp("s3"))
        val items = advise(cps, commits = listOf(userCommit("s2")))
        assertNull(items.firstOrNull { it.kind == AdviceKind.LONG_STRETCH_WITHOUT_COMMIT })
    }

    // ---- REFACTORING_INTRODUCED_SMELLS ----

    @Test
    fun `refactoring introduced smells fires when a step's toSha first-saw a violation`() {
        val cps = listOf(
            cp("s1"),
            cp("s2", pmdTracking = PmdTracking(firstSeenAtSha = listOf("s2"))),
        )
        val step = refStep(toSha = "s2", ideRelevant = true)
        val item = advise(cps, refactoringSteps = listOf(step))
            .single { it.kind == AdviceKind.REFACTORING_INTRODUCED_SMELLS }
        assertEquals(AdviceSeverity.WARNING, item.severity)
        assertTrue("1 refactoring" in item.body)
    }

    @Test
    fun `refactoring introduced smells does not fire when all violations pre-existed`() {
        val cps = listOf(
            cp("s1"),
            cp("s2", pmdTracking = PmdTracking(firstSeenAtSha = listOf("s1"))),
        )
        val step = refStep(toSha = "s2", ideRelevant = true)
        assertNull(
            advise(cps, refactoringSteps = listOf(step))
                .firstOrNull { it.kind == AdviceKind.REFACTORING_INTRODUCED_SMELLS },
        )
    }

    // ---- SMELLS_ACCUMULATED_NET ----

    @Test
    fun `smells accumulated net fires when end exceeds start by more than two`() {
        val cps = listOf(
            cp("s1", smells = 1),
            cp("s2", smells = 8),
        )
        val item = advise(cps).single { it.kind == AdviceKind.SMELLS_ACCUMULATED_NET }
        assertEquals(AdviceSeverity.WARNING, item.severity)
        assertTrue("1 → 8" in item.body)
        assertTrue("7 more" in item.body)
    }

    @Test
    fun `smells accumulated net does not fire on flat trajectories`() {
        val cps = listOf(cp("s1", smells = 5), cp("s2", smells = 5))
        assertNull(advise(cps).firstOrNull { it.kind == AdviceKind.SMELLS_ACCUMULATED_NET })
    }

    // ---- PROCESS_SCORE_DEGRADED ----

    @Test
    fun `process score degraded fires WARNING between -5 and -14 points`() {
        val cps = listOf(cp("s1", processTotal = 80), cp("s2", processTotal = 70))
        val item = advise(cps).single { it.kind == AdviceKind.PROCESS_SCORE_DEGRADED }
        assertEquals(AdviceSeverity.WARNING, item.severity)
        assertTrue("80 to 70" in item.body)
    }

    @Test
    fun `process score degraded fires CRITICAL at -15 or worse`() {
        val cps = listOf(cp("s1", processTotal = 80), cp("s2", processTotal = 60))
        val item = advise(cps).single { it.kind == AdviceKind.PROCESS_SCORE_DEGRADED }
        assertEquals(AdviceSeverity.CRITICAL, item.severity)
    }

    @Test
    fun `process score degraded does not fire on improvement or noise`() {
        val cps = listOf(cp("s1", processTotal = 60), cp("s2", processTotal = 58))
        assertNull(advise(cps).firstOrNull { it.kind == AdviceKind.PROCESS_SCORE_DEGRADED })
    }

    // ---- REORDER_BEATS_USER ----

    @Test
    fun `reorder beats user fires when a multi-step alt scores above user terminal`() {
        // User ends at process=60 on s3. Alt is a 2-step reorder ending at
        // synthesised altb with process=70.
        val cps = listOf(
            cp("s1", processTotal = 50),
            cp("s2", processTotal = 55),
            cp("s3", processTotal = 60),
        )
        val alt = AlternativeTrajectory(
            stepIndexes = listOf(1, 0),
            fromSha = "s1",
            userToSha = "s3",
            branchRefs = listOf("r1", "r2"),
            specs = listOf(RefactoringSpec.Other, RefactoringSpec.Other),
            altCheckpoints = listOf(
                cp("alta", processTotal = 55),
                cp("altb", processTotal = 70),
            ),
        )
        val item = advise(cps, alternatives = listOf(alt))
            .single { it.kind == AdviceKind.REORDER_BEATS_USER }
        assertEquals(AdviceSeverity.INFO, item.severity)
        assertTrue("10 points" in item.body)
    }

    @Test
    fun `reorder beats user ignores single-step alts even if they win`() {
        val cps = listOf(
            cp("s1", processTotal = 50),
            cp("s2", processTotal = 60),
        )
        val alt = AlternativeTrajectory(
            stepIndexes = listOf(0),
            fromSha = "s1",
            userToSha = "s2",
            branchRefs = listOf("r1"),
            specs = listOf(RefactoringSpec.Other),
            altCheckpoints = listOf(cp("alta", processTotal = 80)),
        )
        assertNull(
            advise(cps, alternatives = listOf(alt))
                .firstOrNull { it.kind == AdviceKind.REORDER_BEATS_USER },
        )
    }

    @Test
    fun `reorder beats user does not fire when delta is under three points`() {
        val cps = listOf(cp("s1", processTotal = 50), cp("s2", processTotal = 60))
        val alt = AlternativeTrajectory(
            stepIndexes = listOf(1, 0),
            fromSha = "s1",
            userToSha = "s2",
            branchRefs = listOf("r1", "r2"),
            specs = listOf(RefactoringSpec.Other, RefactoringSpec.Other),
            altCheckpoints = listOf(
                cp("alta", processTotal = 58),
                cp("altb", processTotal = 62),
            ),
        )
        assertNull(
            advise(cps, alternatives = listOf(alt))
                .firstOrNull { it.kind == AdviceKind.REORDER_BEATS_USER },
        )
    }

    // ---- aggregate ----

    @Test
    fun `pristine trajectory produces no advice`() {
        val cps = listOf(
            cp("s1", processTotal = 50, smells = 0),
            cp("s2", processTotal = 55, smells = 0),
            cp("s3", processTotal = 60, smells = 0),
        )
        val items = advise(cps, commits = listOf(userCommit("s2")))
        assertEquals(emptyList(), items)
    }

    @Test
    fun `multiple rules can fire on the same report`() {
        val cps = listOf(
            cp("s1", buildSuccess = false, processTotal = 80, smells = 1),
            cp("s2", buildSuccess = false, processTotal = 60, smells = 10),
            cp("s3", buildSuccess = false, processTotal = 50, smells = 12),
        )
        val items = advise(cps)
        val kinds = items.map { it.kind }.toSet()
        assertTrue(AdviceKind.BUILD_OFTEN_BROKEN in kinds)
        assertTrue(AdviceKind.SMELLS_ACCUMULATED_NET in kinds)
        assertTrue(AdviceKind.PROCESS_SCORE_DEGRADED in kinds)
        assertTrue(AdviceKind.LONG_STRETCH_WITHOUT_COMMIT in kinds)
    }

    // ---- fixtures ----

    private fun advise(
        checkpoints: List<CheckpointReport>,
        commits: List<UserGitCommit> = emptyList(),
        refactoringSteps: List<RefactoringStep> = emptyList(),
        alternatives: List<AlternativeTrajectory> = emptyList(),
    ) = TrajectoryAdvisor.advise(
        report(
            checkpoints = checkpoints,
            commits = commits,
            refactoringSteps = refactoringSteps,
            alternatives = alternatives,
        ),
    )

    private fun report(
        checkpoints: List<CheckpointReport>,
        commits: List<UserGitCommit> = emptyList(),
        refactoringSteps: List<RefactoringStep> = emptyList(),
        alternatives: List<AlternativeTrajectory> = emptyList(),
    ): AnalysisReport = AnalysisReport(
        session = SessionMetadata(
            sessionId = "test",
            name = "s",
            projectName = "p",
            projectPath = "/p",
            startTime = 0,
            ideVersion = "x",
            pluginVersion = "x",
        ),
        run = com.github.ethanhosier.analysis.metrics.model.RunInfo(
            parallelism = 1, generatedAt = 0, metricsDurationMs = 0,
        ),
        checkpoints = checkpoints,
        refactoringSteps = refactoringSteps,
        userGitCommits = commits,
        alternativeTrajectories = alternatives,
    )

    private fun cp(
        sha: String,
        buildSuccess: Boolean = true,
        testsSuccess: Boolean = true,
        testsSkipped: Boolean = false,
        smells: Int = 0,
        processTotal: Int = 50,
        pmdTracking: PmdTracking = PmdTracking.EMPTY,
    ): CheckpointReport = CheckpointReport(
        sha = sha,
        events = emptyList(),
        metrics = CheckpointMetrics(
            sha = sha,
            ck = CkResult(perClass = emptyList()),
            pmd = PmdResult(
                violations = emptyList(),
                classMetrics = emptyList(),
                methodMetrics = emptyList(),
            ),
            cpd = CpdResult.EMPTY,
            readability = ReadabilityResult.EMPTY,
            build = BuildResult(
                success = buildSuccess, exitCode = if (buildSuccess) 0 else 1,
                durationMs = 0, timedOut = false, stderrTail = "",
            ),
            tests = if (testsSkipped) {
                TestResult.skipped("build failed")
            } else {
                TestResult(
                    success = testsSuccess,
                    exitCode = if (testsSuccess) 0 else 1,
                    durationMs = 0, timedOut = false, total = 0, passed = 0,
                    failed = 0, skipped = 0, failures = emptyList(),
                    stderrTail = "", wasSkipped = false,
                )
            },
        ),
        pmdTracking = pmdTracking,
        derivedMetrics = DerivedMetrics(
            smells = smells,
            process = ProcessScore(
                total = processTotal, baseline = 50, clamped = false,
                contributions = emptyList(),
            ),
        ),
    )

    private fun userCommit(sha: String): UserGitCommit = UserGitCommit(
        sha = sha, parentSha = null, timestamp = 0, message = "m", action = "commit",
    )

    private fun refStep(toSha: String, ideRelevant: Boolean): RefactoringStep =
        RefactoringStep(
            stepIndex = 0,
            fromSha = "prev",
            toSha = toSha,
            toCheckpointIndex = 1,
            timestamp = 0,
            refactoring = DetectedRefactoring(
                type = "Extract Method",
                description = "Extract Method",
                leftSideLocations = emptyList(),
                rightSideLocations = emptyList(),
                ideRelevant = ideRelevant,
            ),
        )
}
