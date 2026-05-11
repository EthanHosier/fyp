package com.github.ethanhosier.analysis.metrics.derived

import com.github.ethanhosier.analysis.metrics.ck.CkClassMetrics
import com.github.ethanhosier.analysis.metrics.ck.CkResult
import com.github.ethanhosier.analysis.metrics.cpd.CpdResult
import com.github.ethanhosier.analysis.metrics.gradlebuild.BuildResult
import com.github.ethanhosier.analysis.metrics.model.AlternativeTrajectory
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.metrics.model.CheckpointReport
import com.github.ethanhosier.analysis.metrics.model.EventSummary
import com.github.ethanhosier.analysis.metrics.model.PmdTracking
import com.github.ethanhosier.analysis.metrics.pmd.PmdMethodMetrics
import com.github.ethanhosier.analysis.metrics.pmd.PmdResult
import com.github.ethanhosier.analysis.metrics.pmd.PmdViolation
import com.github.ethanhosier.analysis.metrics.readability.ReadabilityResult
import com.github.ethanhosier.analysis.metrics.readability.ReadabilitySummary
import com.github.ethanhosier.analysis.metrics.tests.TestResult
import com.github.ethanhosier.analysis.miner.model.DetectedRefactoring
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.ideplugin.model.EventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.math.absoluteValue

class DerivedMetricsRunnerTest {

    @Test
    fun `aggregators reduce raw metric blocks per checkpoint`() {
        val cp = checkpoint(
            sha = "a",
            ck = ckResult(
                ckClass(cbo = 5, tcc = 0.8f),
                ckClass(cbo = 10, tcc = 0.4f),
                ckClass(cbo = 1, tcc = null),
            ),
            cpd = CpdResult.EMPTY.copy(totalLines = 100, duplicatedLinesShare = 0.123),
            readability = ReadabilityResult.EMPTY.copy(summary = readabilitySummary()),
            pmd = pmdResult(
                methodMetrics = listOf(methodMetric(cognitive = 3), methodMetric(cognitive = 7)),
                violations = listOf(violation(priority = 1), violation(priority = 5)),
            ),
        )

        val result = DerivedMetricsRunner().run(listOf(cp), emptyList(), emptyList())
        val d = result.main.getValue("a")

        // P90 of [5, 10, 1] (sorted [1, 5, 10]) at p=0.9: ceil(0.9*3)=3, idx=2 → 10.0
        assertEquals(10.0, d.coupling)
        // mean of non-null TCC [0.8, 0.4] = 0.6, rounded to 2dp
        assertEquals(0.6, d.cohesion)
        assertEquals(12.3, d.duplication)
        assertNotNull(d.readability)
        assertEquals(10, d.cognitive)
        assertEquals(2, d.smells)
    }

    @Test
    fun `empty CK collapses coupling to zero and cohesion to null`() {
        // Mirrors the frontend's view-model: coupling is always emitted
        // (percentile of empty falls back to 0), cohesion is the only
        // sub-metric that can legitimately be missing.
        val cp = checkpoint(sha = "a", ck = CkResult(perClass = emptyList()))
        val result = DerivedMetricsRunner().run(listOf(cp), emptyList(), emptyList())
        val d = result.main.getValue("a")
        assertEquals(0.0, d.coupling)
        assertNull(d.cohesion)
    }

    @Test
    fun `cleanliness flags rebased when a sub-metric range is degenerate`() {
        // Two checkpoints with cohesion missing on both → cohesion's
        // weight (0.05) is redistributed; rebased = true.
        val a = checkpoint("a", ck = ckResult(ckClass(cbo = 1, tcc = null)))
        val b = checkpoint("b", ck = ckResult(ckClass(cbo = 2, tcc = null)))
        val result = DerivedMetricsRunner().run(listOf(a, b), emptyList(), emptyList())

        val cleanliness = result.main.getValue("a").cleanliness
        assertNotNull(cleanliness)
        assertTrue(cleanliness.rebased)
        assertTrue(cleanliness.contributions.none { it.id == "cohesion" })
    }

    @Test
    fun `process score moves up when cleanliness improves and nothing is broken`() {
        val a = checkpoint("a", ck = ckResult(ckClass(cbo = 10, tcc = 0.2f)))
        val b = checkpoint("b", ck = ckResult(ckClass(cbo = 1, tcc = 0.9f)))
        val result = DerivedMetricsRunner().run(listOf(a, b), emptyList(), emptyList())

        val a0 = result.main.getValue("a").process
        val b0 = result.main.getValue("b").process
        assertEquals(50, a0.total)            // baseline at c0 — gain is 0 vs self
        assertTrue(b0.total > 50, "b should score above baseline; got ${b0.total}")
    }

    @Test
    fun `broken checkpoint penalises process score`() {
        val a = checkpoint("a", ck = ckResult(ckClass(cbo = 1, tcc = 0.9f)))
        val broken = checkpoint(
            "b",
            ck = ckResult(ckClass(cbo = 1, tcc = 0.9f)),
            build = passingBuild().copy(success = false),
        )
        val result = DerivedMetricsRunner().run(listOf(a, broken), emptyList(), emptyList())
        val b = result.main.getValue("b").process
        val brokenContrib = b.contributions.single { it.id == "broken" }
        assertTrue(brokenContrib.points < 0.0, "broken term should be negative")
    }

    @Test
    fun `manual-when-IDE penalty fires when an IDE-relevant step was performed manually`() {
        val a = checkpoint("a")
        val b = checkpoint("b")
        val step = RefactoringStep(
            stepIndex = 0,
            fromSha = "a",
            toSha = "b",
            toCheckpointIndex = 1,
            timestamp = 0L,
            refactoring = detected("Extract Method", ideRelevant = true),
            wasPerformedByIde = false,
        )
        val result = DerivedMetricsRunner().run(listOf(a, b), emptyList(), listOf(step))
        val manual = result.main.getValue("b").process.contributions.single { it.id == "manualIde" }
        assertTrue(manual.points < 0.0)
    }

    @Test
    fun `skipped tests after a refactoring step penalise the score`() {
        val a = checkpoint("a")
        val b = checkpoint("b")  // no TEST_RUN_FINISHED event ⇒ tests skipped
        val step = RefactoringStep(
            stepIndex = 0,
            fromSha = "a",
            toSha = "b",
            toCheckpointIndex = 1,
            timestamp = 0L,
            refactoring = detected("Move Method", ideRelevant = false),
            wasPerformedByIde = true,
        )
        val result = DerivedMetricsRunner().run(listOf(a, b), emptyList(), listOf(step))
        val skip = result.main.getValue("b").process.contributions.single { it.id == "skipTests" }
        assertTrue(skip.points < 0.0)
    }

    @Test
    fun `running tests after a refactor stops the skip penalty from firing`() {
        val a = checkpoint("a")
        val b = checkpoint(
            "b",
            events = listOf(EventSummary(id = "e", type = EventType.TEST_RUN_FINISHED, timestamp = 0L)),
        )
        val step = RefactoringStep(
            stepIndex = 0,
            fromSha = "a",
            toSha = "b",
            toCheckpointIndex = 1,
            timestamp = 0L,
            refactoring = detected("Move Method", ideRelevant = false),
            wasPerformedByIde = true,
        )
        val result = DerivedMetricsRunner().run(listOf(a, b), emptyList(), listOf(step))
        val skip = result.main.getValue("b").process.contributions.single { it.id == "skipTests" }
        // `assertEquals(0.0, x)` would fail on `-0.0` (strict-equality on
        // signed zeros); we only care that no penalty is applied.
        assertEquals(0.0, skip.points.absoluteValue)
    }

    @Test
    fun `alt process score reads main snapshot at fromSha and adds one synthetic step`() {
        // Main: a clean → b clean. Alt branches off `a` with worse
        // cleanliness — its process score should be lower than b's
        // because the alt step adds a manual-when-IDE-style penalty
        // (skipped tests on the synthesised path).
        val a = checkpoint("a", ck = ckResult(ckClass(cbo = 5, tcc = 0.5f)))
        val b = checkpoint("b", ck = ckResult(ckClass(cbo = 1, tcc = 0.9f)))
        val altCheckpoint = checkpoint("alt", ck = ckResult(ckClass(cbo = 1, tcc = 0.9f)))
        val alt = AlternativeTrajectory(
            stepIndexes = listOf(0),
            fromSha = "a",
            userToSha = "b",
            branchRefs = listOf("refs/heads/alt"),
            specs = listOf(RefactoringSpec.Other),
            altCheckpoints = listOf(altCheckpoint),
        )

        val result = DerivedMetricsRunner().run(listOf(a, b), listOf(alt), emptyList())
        val altProc = result.alt.getValue("alt").process
        // skipTests fires (synthesised commit ⇒ no user-run tests after it),
        // manualIde does NOT fire (alt was performed by the IDE).
        val skip = altProc.contributions.single { it.id == "skipTests" }
        val manual = altProc.contributions.single { it.id == "manualIde" }
        assertTrue(skip.points < 0.0, "alt synthesises a skipped-tests step")
        assertEquals(0.0, manual.points.absoluteValue, "alt step is IDE-driven by definition")
    }

    @Test
    fun `smell load is time-integral and churn-invariant on identical end state`() {
        // Two trajectories, three checkpoints each, identical pmd at the
        // terminus (one priority-3 violation open = weight 3). Path A is
        // monotone: introduces it at c1, leaves it. Path B churns: adds
        // 5 new violations at c1 (4 extra noise + the keeper) then resolves
        // 4 of them at c2 leaving the same single violation. Under the
        // old fraction-based formula B scored better (smaller open/seen
        // ratio); under the time-integral formula B must score WORSE (it
        // briefly carried a heavier smell load).
        val seed = checkpoint(
            "seed0",
            pmd = pmdResult(),
            pmdTracking = PmdTracking.EMPTY,
        )

        // ---- path A: introduce the single keeper at c1, carry it.
        val keeperA = violation(priority = 3)
        val a1 = checkpoint(
            "a1",
            pmd = pmdResult(violations = listOf(keeperA)),
            pmdTracking = PmdTracking(firstSeenAtSha = listOf("a1")),
        )
        val a2 = checkpoint(
            "a2",
            pmd = pmdResult(violations = listOf(keeperA)),
            pmdTracking = PmdTracking(firstSeenAtSha = listOf("a1")),
        )
        val aRes = DerivedMetricsRunner().run(listOf(seed, a1, a2), emptyList(), emptyList())
        val aSmells = aRes.main.getValue("a2").process.contributions.single { it.id == "smells" }

        // ---- path B: introduce keeper + 4 noise at c1, resolve noise at c2.
        val keeperB = violation(priority = 3)
        val noiseB = List(4) { violation(priority = 3) }
        val b1 = checkpoint(
            "b1",
            pmd = pmdResult(violations = noiseB + keeperB),
            pmdTracking = PmdTracking(firstSeenAtSha = List(5) { "b1" }),
        )
        val b2 = checkpoint(
            "b2",
            // Only keeperB carries forward; 4 noise resolved.
            pmd = pmdResult(violations = listOf(keeperB)),
            pmdTracking = PmdTracking(firstSeenAtSha = listOf("b1")),
        )
        val bRes = DerivedMetricsRunner().run(listOf(seed, b1, b2), emptyList(), emptyList())
        val bSmells = bRes.main.getValue("b2").process.contributions.single { it.id == "smells" }

        // Churn should be punished, not rewarded: B's intermediate dip is
        // genuine smell load that the user lived through.
        assertTrue(
            bSmells.points < aSmells.points,
            "churn-heavy path B should score worse than monotone A: A=${aSmells.points} B=${bSmells.points}",
        )
    }

    @Test
    fun `seed-checkpoint preexisting violations are not counted as added`() {
        // Seed has 2 violations both stamped at its own sha; with the
        // frontend bucketing rule those count as carried, not added — so
        // the smells contribution at c0 should be 0.
        val seedViolations = listOf(violation(priority = 1), violation(priority = 1))
        val cp = checkpoint(
            "seed",
            pmd = pmdResult(violations = seedViolations),
            pmdTracking = PmdTracking(firstSeenAtSha = listOf("seed", "seed")),
        )
        val next = checkpoint("next")
        val result = DerivedMetricsRunner().run(listOf(cp, next), emptyList(), emptyList())
        val smells = result.main.getValue("seed").process.contributions.single { it.id == "smells" }
        assertEquals(0.0, smells.points.absoluteValue)
    }

    // ---- fixtures ----

    private fun checkpoint(
        sha: String,
        ck: CkResult = ckResult(ckClass(cbo = 1, tcc = 0.5f)),
        cpd: CpdResult = CpdResult.EMPTY,
        readability: ReadabilityResult = ReadabilityResult.EMPTY,
        pmd: PmdResult = pmdResult(),
        build: BuildResult = passingBuild(),
        tests: TestResult = passingTests(),
        events: List<EventSummary> = emptyList(),
        pmdTracking: PmdTracking = PmdTracking.EMPTY,
    ): CheckpointReport = CheckpointReport(
        sha = sha,
        events = events,
        metrics = CheckpointMetrics(
            sha = sha,
            ck = ck,
            pmd = pmd,
            cpd = cpd,
            readability = readability,
            build = build,
            tests = tests,
        ),
        pmdTracking = pmdTracking,
    )

    private fun ckResult(vararg classes: CkClassMetrics): CkResult =
        CkResult(perClass = classes.toList())

    private fun ckClass(cbo: Int, tcc: Float?): CkClassMetrics = CkClassMetrics(
        className = "C$cbo",
        file = "C$cbo.java",
        type = "class",
        cbo = cbo,
        cboModified = cbo,
        fanin = 0,
        fanout = cbo,
        wmc = 1,
        rfc = 0,
        lcom = 0,
        lcomNormalized = null,
        tcc = tcc,
        lcc = tcc,
        dit = 1,
        noc = 0,
        loc = 10,
        numberOfMethods = 2,
        numberOfFields = 0,
        returnQty = 0,
        loopQty = 0,
        comparisonsQty = 0,
        tryCatchQty = 0,
        variablesQty = 0,
        maxNestedBlocks = 0,
        uniqueWordsQty = 0,
    )

    private fun pmdResult(
        methodMetrics: List<PmdMethodMetrics> = emptyList(),
        violations: List<PmdViolation> = emptyList(),
    ): PmdResult = PmdResult(
        violations = violations,
        classMetrics = emptyList(),
        methodMetrics = methodMetrics,
    )

    private fun methodMetric(cognitive: Int): PmdMethodMetrics = PmdMethodMetrics(
        className = "C",
        signature = "m()",
        file = "C.java",
        cyclo = 1,
        cognitive = cognitive,
        npath = "1",
        ncss = 1,
        atfd = 0,
    )

    private fun violation(priority: Int): PmdViolation = PmdViolation(
        file = "C.java",
        rule = "X",
        ruleSet = "y",
        priority = priority,
        beginLine = 1,
        endLine = 1,
        message = "m",
        snippet = null,
    )

    private fun passingBuild(): BuildResult = BuildResult(
        success = true, exitCode = 0, durationMs = 0, timedOut = false, stderrTail = "",
    )

    private fun passingTests(): TestResult = TestResult(
        success = true, exitCode = 0, durationMs = 0, timedOut = false,
        total = 0, passed = 0, failed = 0, skipped = 0,
        failures = emptyList(), stderrTail = "", wasSkipped = false,
    )

    private fun detected(type: String, ideRelevant: Boolean): DetectedRefactoring = DetectedRefactoring(
        type = type,
        description = "$type detected",
        leftSideLocations = emptyList(),
        rightSideLocations = emptyList(),
        ideRelevant = ideRelevant,
    )

    private fun readabilitySummary(): ReadabilitySummary = ReadabilitySummary.EMPTY.copy(
        fileCount = 1,
        avgLineLength = 60.0,
        avgIndentation = 4.0,
        avgIdentifierLength = 6.0,
        singleLetterRatio = 0.05,
        dictionaryWordRatio = 0.85,
    )
}
