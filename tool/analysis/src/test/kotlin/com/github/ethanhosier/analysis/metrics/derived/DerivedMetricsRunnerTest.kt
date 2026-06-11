package com.github.ethanhosier.analysis.metrics.derived

import com.github.ethanhosier.analysis.metrics.ck.CkClassMetrics
import com.github.ethanhosier.analysis.metrics.ck.CkResult
import com.github.ethanhosier.analysis.metrics.cpd.CpdDuplication
import com.github.ethanhosier.analysis.metrics.cpd.CpdOccurrence
import com.github.ethanhosier.analysis.metrics.cpd.CpdResult
import com.github.ethanhosier.analysis.metrics.gitdiff.DiffStats
import com.github.ethanhosier.analysis.metrics.gitdiff.PerFileChurn
import com.github.ethanhosier.analysis.metrics.gradlebuild.BuildResult
import com.github.ethanhosier.analysis.metrics.model.AlternativeTrajectory
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.pipeline.CheckpointReport
import com.github.ethanhosier.analysis.pipeline.EventSummary
import com.github.ethanhosier.analysis.pipeline.PmdTracking
import com.github.ethanhosier.analysis.metrics.pmd.PmdMethodMetrics
import com.github.ethanhosier.analysis.metrics.pmd.PmdResult
import com.github.ethanhosier.analysis.metrics.pmd.PmdViolation
import com.github.ethanhosier.analysis.metrics.readability.FileReadability
import com.github.ethanhosier.analysis.metrics.readability.IdentifierStats
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
        // 100-line touched file with one 20-line clone-group occurrence
        // inside it → duplication = 20 / 100 * 100 = 20.0.
        val cFile = fileReadability(file = "C.java", totalLines = 100)
        val c1File = fileReadability(file = "C1.java", totalLines = 50)
        val c5File = fileReadability(file = "C5.java", totalLines = 50)
        val c10File = fileReadability(file = "C10.java", totalLines = 50)
        val cp = checkpoint(
            sha = "a",
            ck = ckResult(
                ckClass(cbo = 5, tcc = 0.8f),
                ckClass(cbo = 10, tcc = 0.4f),
                ckClass(cbo = 1, tcc = null),
            ),
            cpd = CpdResult.EMPTY.copy(
                duplications = listOf(
                    CpdDuplication(
                        tokens = 0, lines = 20,
                        occurrences = listOf(
                            CpdOccurrence(file = "C.java", beginLine = 1, endLine = 20),
                        ),
                    ),
                ),
            ),
            readability = ReadabilityResult.EMPTY.copy(
                perFile = listOf(cFile, c1File, c5File, c10File),
                summary = readabilitySummary(),
            ),
            pmd = pmdResult(
                methodMetrics = listOf(methodMetric(cognitive = 3), methodMetric(cognitive = 7)),
                violations = listOf(violation(priority = 1), violation(priority = 5)),
            ),
        )

        val result = DerivedMetricsRunner().run(listOf(cp), emptyList(), emptyList())
        val d = result.main.getValue("a")

        // Mean of CBO over touched classes [5, 10, 1] = 5.333…, round1 = 5.3.
        assertEquals(5.3, d.coupling)
        // Mean of non-null TCC [0.8, 0.4] = 0.6, rounded to 2dp.
        assertEquals(0.6, d.cohesion)
        // Touched-file duplication rate: 20 dup lines / 250 touched lines × 100 = 8.0.
        assertEquals(8.0, d.duplication)
        assertNotNull(d.readability)
        // Mean cognitive over touched methods = (3 + 7) / 2 = 5.
        assertEquals(5, d.cognitive)
        assertEquals(2, d.smells)
    }

    @Test
    fun `empty CK collapses coupling to zero and cohesion to null`() {
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
        assertTrue(a0.total <= 50, "c0 should sit at-or-below baseline; got ${a0.total}")
        assertTrue(b0.total > 50, "b should score above baseline; got ${b0.total}")
        assertTrue(b0.total > a0.total, "trajectory should climb; got a=${a0.total} b=${b0.total}")
    }

    @Test
    fun `broken checkpoint penalises process score`() {
        // Stamp timestamps so `brokenMs / elapsedMs` actually accumulates;
        // a → b is 1s, both broken intervals contribute the full second.
        val a = checkpoint(
            "a",
            ck = ckResult(ckClass(cbo = 1, tcc = 0.9f)),
            events = listOf(syntheticEvent(timestamp = 0L)),
        )
        val broken = checkpoint(
            "b",
            ck = ckResult(ckClass(cbo = 1, tcc = 0.9f)),
            build = passingBuild().copy(success = false),
            events = listOf(syntheticEvent(timestamp = 1_000L), syntheticEvent(timestamp = 2_000L)),
        )
        val result = DerivedMetricsRunner().run(listOf(a, broken), emptyList(), emptyList())
        val b = result.main.getValue("b").process
        val brokenContrib = b.contributions.single { it.id == "broken" }
        assertTrue(brokenContrib.points < 0.0, "broken term should be negative")
    }

    @Test
    fun `build-broken middle checkpoint carries forward cleanliness from prior trustworthy`() {
        val a = checkpoint(
            "a",
            ck = ckResult(ckClass(cbo = 1, tcc = 0.9f)),
            events = listOf(syntheticEvent(timestamp = 0L)),
        )
        val b = checkpoint(
            "b",
            ck = ckResult(),  // empty perClass — coupling would degenerate
            pmd = pmdResult(),
            cpd = CpdResult.EMPTY,
            readability = ReadabilityResult.EMPTY,
            build = passingBuild().copy(success = false),
            tests = TestResult.skipped("build failed"),
            events = listOf(syntheticEvent(timestamp = 1_000L)),
        )
        val c = checkpoint(
            "c",
            ck = ckResult(ckClass(cbo = 1, tcc = 0.9f)),
            events = listOf(syntheticEvent(timestamp = 2_000L)),
        )

        val result = DerivedMetricsRunner().run(listOf(a, b, c), emptyList(), emptyList())

        val aClean = result.main.getValue("a").cleanliness
        val bClean = result.main.getValue("b").cleanliness
        assertEquals(aClean, bClean, "broken b carries forward a's cleanliness")

        val bTrust = result.mainTrust.getValue("b")
        assertEquals(false, bTrust.trustworthy)
        assertEquals("PRIOR", bTrust.source)
        assertEquals(true, result.mainTrust.getValue("a").trustworthy)
        assertEquals(true, result.mainTrust.getValue("c").trustworthy)

        // W_BROKEN still fires on b — the trust signal doesn't suppress
        // the existing broken-checkpoint penalty.
        val bBroken = result.main.getValue("b").process.contributions
            .single { it.id == "broken" }
        assertTrue(bBroken.points < 0.0, "broken term still penalises b")
    }

    @Test
    fun `tests-broken middle checkpoint also carries forward (blocks gaming)`() {
        val a = checkpoint("a")
        val b = checkpoint(
            "b",
            ck = ckResult(),
            tests = passingTests().copy(success = false, failed = 1, total = 1),
        )
        val c = checkpoint("c")

        val result = DerivedMetricsRunner().run(listOf(a, b, c), emptyList(), emptyList())
        val bTrust = result.mainTrust.getValue("b")
        assertEquals(false, bTrust.trustworthy)
        assertEquals("PRIOR", bTrust.source)
        assertEquals(
            result.main.getValue("a").cleanliness,
            result.main.getValue("b").cleanliness,
            "tests-failed b carries forward a's cleanliness",
        )
    }

    @Test
    fun `leading untrustworthy gap is filled with session midpoint`() {
        val a = checkpoint(
            "a",
            ck = ckResult(),
            build = passingBuild().copy(success = false),
            tests = TestResult.skipped("build failed"),
        )
        val b = checkpoint("b", ck = ckResult(ckClass(cbo = 1, tcc = 0.9f)))
        val c = checkpoint("c", ck = ckResult(ckClass(cbo = 5, tcc = 0.5f)))

        val result = DerivedMetricsRunner().run(listOf(a, b, c), emptyList(), emptyList())

        val aTrust = result.mainTrust.getValue("a")
        assertEquals(false, aTrust.trustworthy)
        assertEquals("MIDPOINT", aTrust.source)
        assertEquals(true, result.mainTrust.getValue("b").trustworthy)
        assertEquals(true, result.mainTrust.getValue("c").trustworthy)
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
    fun `consecutive refactor steps within 60s collapse into one composite`() {
        val a = checkpoint("a", events = listOf(syntheticEvent(0L)))
        val b = checkpoint("b", events = listOf(syntheticEvent(20_000L)))
        val c = checkpoint("c", events = listOf(syntheticEvent(40_000L)))
        val d = checkpoint("d", events = listOf(syntheticEvent(50_000L)))
        val steps = listOf(
            RefactoringStep(
                stepIndex = 0, fromSha = "a", toSha = "b", toCheckpointIndex = 1,
                timestamp = 20_000L,
                refactoring = detected("Extract Method", ideRelevant = false),
                wasPerformedByIde = true,
            ),
            RefactoringStep(
                stepIndex = 1, fromSha = "b", toSha = "c", toCheckpointIndex = 2,
                timestamp = 40_000L,
                refactoring = detected("Extract Method", ideRelevant = false),
                wasPerformedByIde = true,
            ),
            RefactoringStep(
                stepIndex = 2, fromSha = "c", toSha = "d", toCheckpointIndex = 3,
                timestamp = 50_000L,
                refactoring = detected("Extract Method", ideRelevant = false),
                wasPerformedByIde = true,
            ),
        )
        val result = DerivedMetricsRunner().run(listOf(a, b, c, d), emptyList(), steps)
        val skip = result.main.getValue("d").process.contributions.single { it.id == "skipTests" }
        // "1 of 1 refactoring batch not followed by tests"
        assertTrue("1 of 1 refactoring batch" in skip.detail, "detail was: ${skip.detail}")
    }

    @Test
    fun `refactor steps more than 60s apart form separate composites`() {
        // Step A at t=0 and step B at t=70s (>60s gap) → 2 composites.
        // No tests → 2 of 2 composites skipped.
        val a = checkpoint("a", events = listOf(syntheticEvent(0L)))
        val b = checkpoint("b", events = listOf(syntheticEvent(0L)))
        val c = checkpoint("c", events = listOf(syntheticEvent(70_000L)))
        val steps = listOf(
            RefactoringStep(
                stepIndex = 0, fromSha = "a", toSha = "b", toCheckpointIndex = 1,
                timestamp = 0L,
                refactoring = detected("Move Method", ideRelevant = false),
                wasPerformedByIde = true,
            ),
            RefactoringStep(
                stepIndex = 1, fromSha = "b", toSha = "c", toCheckpointIndex = 2,
                timestamp = 70_000L,
                refactoring = detected("Move Method", ideRelevant = false),
                wasPerformedByIde = true,
            ),
        )
        val result = DerivedMetricsRunner().run(listOf(a, b, c), emptyList(), steps)
        val skip = result.main.getValue("c").process.contributions.single { it.id == "skipTests" }
        assertTrue("2 of 2 refactoring batches" in skip.detail, "detail was: ${skip.detail}")
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
        val a = checkpoint("a", ck = ckResult(ckClass(cbo = 5, tcc = 0.5f)))
        val b = checkpoint("b", ck = ckResult(ckClass(cbo = 1, tcc = 0.9f)))
        val altCheckpoint = checkpoint("alt", ck = ckResult(ckClass(cbo = 1, tcc = 0.9f)))
        val step = RefactoringStep(
            stepIndex = 0,
            fromSha = "a",
            toSha = "b",
            toCheckpointIndex = 1,
            timestamp = 0L,
            refactoring = detected("Move Method", ideRelevant = true),
            wasPerformedByIde = false,
        )
        val alt = AlternativeTrajectory(
            stepIndexes = listOf(0),
            fromSha = "a",
            userToSha = "b",
            branchRefs = listOf("refs/heads/alt"),
            specs = listOf(RefactoringSpec.Other),
            altCheckpoints = listOf(altCheckpoint),
        )

        val result = DerivedMetricsRunner().run(listOf(a, b), listOf(alt), listOf(step))
        val altProc = result.alt.getValue("alt").process
        // skipTests fires (user didn't run tests at the replaced step's
        // landing cp), manualIde does NOT fire (alts are IDE-driven).
        val skip = altProc.contributions.single { it.id == "skipTests" }
        val manual = altProc.contributions.single { it.id == "manualIde" }
        assertTrue(skip.points < 0.0, "alt inherits the user step's skip-tests penalty")
        assertEquals(0.0, manual.points.absoluteValue, "alt step is IDE-driven by definition")
    }

    @Test
    fun `W_LENGTH bonus rewards alt that covers user window in fewer steps`() {
        val a = checkpoint("a", ck = ckResult(ckClass(cbo = 1, tcc = 0.5f)))
        val b = checkpoint("b", ck = ckResult(ckClass(cbo = 1, tcc = 0.5f)))
        val c = checkpoint("c", ck = ckResult(ckClass(cbo = 1, tcc = 0.5f)))
        val d = checkpoint("d", ck = ckResult(ckClass(cbo = 1, tcc = 0.5f)))
        val altCp = checkpoint("alt", ck = ckResult(ckClass(cbo = 1, tcc = 0.5f)))
        val steps = listOf(
            RefactoringStep(
                stepIndex = 0, fromSha = "a", toSha = "b", toCheckpointIndex = 1,
                timestamp = 0L,
                refactoring = detected("Extract Method", ideRelevant = true),
                wasPerformedByIde = false,
            ),
            RefactoringStep(
                stepIndex = 1, fromSha = "b", toSha = "c", toCheckpointIndex = 2,
                timestamp = 0L,
                refactoring = detected("Extract Method", ideRelevant = true),
                wasPerformedByIde = false,
            ),
            RefactoringStep(
                stepIndex = 2, fromSha = "c", toSha = "d", toCheckpointIndex = 3,
                timestamp = 0L,
                refactoring = detected("Extract Method", ideRelevant = true),
                wasPerformedByIde = false,
            ),
        )
        val alt = AlternativeTrajectory(
            kind = com.github.ethanhosier.analysis.pipeline.DivergenceKind.IDE_REPLAY,
            stepIndexes = listOf(0, 1, 2),
            fromSha = "a",
            userToSha = "d",
            branchRefs = listOf("refs/heads/alt"),
            specs = listOf(RefactoringSpec.Other, RefactoringSpec.Other, RefactoringSpec.Other),
            altCheckpoints = listOf(altCp),
        )
        val result = DerivedMetricsRunner().run(listOf(a, b, c, d), listOf(alt), steps)
        val altLength = result.alt.getValue("alt").process.contributions
            .single { it.id == "altLength" }
        // ~7.33 points (within rounding tolerance)
        assertTrue(altLength.points in 7.0..8.0, "expected bonus ≈ 7.33, got ${altLength.points}")
    }

    @Test
    fun `W_LENGTH bonus is zero when alt has same step count as user`() {
        // ORDERING-style alt: same number of steps, just reordered.
        // No step savings → 0 bonus.
        val a = checkpoint("a", ck = ckResult(ckClass(cbo = 1, tcc = 0.5f)))
        val b = checkpoint("b", ck = ckResult(ckClass(cbo = 1, tcc = 0.5f)))
        val altCp = checkpoint("alt", ck = ckResult(ckClass(cbo = 1, tcc = 0.5f)))
        val step = RefactoringStep(
            stepIndex = 0, fromSha = "a", toSha = "b", toCheckpointIndex = 1,
            timestamp = 0L,
            refactoring = detected("Extract Method", ideRelevant = true),
            wasPerformedByIde = false,
        )
        val alt = AlternativeTrajectory(
            stepIndexes = listOf(0),
            fromSha = "a",
            userToSha = "b",
            branchRefs = listOf("refs/heads/alt"),
            specs = listOf(RefactoringSpec.Other),
            altCheckpoints = listOf(altCp),
        )
        val result = DerivedMetricsRunner().run(listOf(a, b), listOf(alt), listOf(step))
        val altLength = result.alt.getValue("alt").process.contributions
            .single { it.id == "altLength" }
        assertEquals(0.0, altLength.points.absoluteValue)
    }

    @Test
    fun `alt continuation extends through remaining user checkpoints`() {
        val a = checkpoint("a")
        val b = checkpoint(
            "b",
            events = listOf(EventSummary(id = "e", type = EventType.TEST_RUN_FINISHED, timestamp = 0L)),
        )
        val c = checkpoint("c")
        val step = RefactoringStep(
            stepIndex = 0,
            fromSha = "a",
            toSha = "b",
            toCheckpointIndex = 1,
            timestamp = 0L,
            refactoring = detected("Extract Method", ideRelevant = true),
            wasPerformedByIde = false,
        )
        val altCp = checkpoint("alt-b")
        val alt = AlternativeTrajectory(
            stepIndexes = listOf(0),
            fromSha = "a",
            userToSha = "b",
            branchRefs = listOf("refs/heads/alt"),
            specs = listOf(RefactoringSpec.Other),
            altCheckpoints = listOf(altCp),
        )

        val result = DerivedMetricsRunner().run(listOf(a, b, c), listOf(alt), listOf(step))

        // One continuation entry — c only (b is the merge point).
        val cont = result.continuations.single()
        assertEquals(listOf("c"), cont.checkpointShas)
        assertEquals(1, cont.processScores.size)

        // Alt avoided the manual penalty; the user's main walk
        // accumulated it. Continuation at c should beat user at c.
        val userAtC = result.main.getValue("c").process
        val altContAtC = cont.processScores.single()
        assertTrue(
            altContAtC.total > userAtC.total,
            "alt continuation at c (=${altContAtC.total}) should beat user at c (=${userAtC.total})",
        )
    }

    @Test
    fun `alt that merges at trace end has empty continuation`() {
        val a = checkpoint("a")
        val b = checkpoint("b")
        val altCp = checkpoint("alt-b")
        val alt = AlternativeTrajectory(
            stepIndexes = listOf(0),
            fromSha = "a",
            userToSha = "b",
            branchRefs = listOf("refs/heads/alt"),
            specs = listOf(RefactoringSpec.Other),
            altCheckpoints = listOf(altCp),
        )

        val result = DerivedMetricsRunner().run(listOf(a, b), listOf(alt), emptyList())

        val cont = result.continuations.single()
        assertTrue(cont.checkpointShas.isEmpty())
        assertTrue(cont.processScores.isEmpty())
    }

    @Test
    fun `alt continuation applies main-walk manual penalty for post-merge user steps`() {
        val a = checkpoint("a")
        val b = checkpoint("b")
        val c = checkpoint("c")
        val altCp = checkpoint("alt-b")
        val alt = AlternativeTrajectory(
            stepIndexes = listOf(0),
            fromSha = "a",
            userToSha = "b",
            branchRefs = listOf("refs/heads/alt"),
            specs = listOf(RefactoringSpec.Other),
            altCheckpoints = listOf(altCp),
        )
        val postMergeStep = RefactoringStep(
            stepIndex = 1,
            fromSha = "b",
            toSha = "c",
            toCheckpointIndex = 2,
            timestamp = 0L,
            refactoring = detected("Extract Method", ideRelevant = true),
            wasPerformedByIde = false,
        )

        val result = DerivedMetricsRunner().run(listOf(a, b, c), listOf(alt), listOf(postMergeStep))

        val contAtC = result.continuations.single().processScores.single()
        val manual = contAtC.contributions.single { it.id == "manualIde" }
        assertTrue(manual.points < 0.0, "manualIde penalty must fire for post-merge user step")
    }

    @Test
    fun `aggregators filter to the trajectory-touched file set`() {
        val touchedClass = CkClassMetrics(
            className = "A", file = "A.java", type = "class",
            cbo = 4, cboModified = 4, fanin = 0, fanout = 4,
            wmc = 1, rfc = 0, lcom = 0, lcomNormalized = null,
            tcc = 0.4f, lcc = 0.4f, dit = 1, noc = 0,
            loc = 10, numberOfMethods = 2, numberOfFields = 0,
            returnQty = 0, loopQty = 0, comparisonsQty = 0, tryCatchQty = 0,
            variablesQty = 0, maxNestedBlocks = 0, uniqueWordsQty = 0,
        )
        val untouchedClass = touchedClass.copy(
            className = "B", file = "B.java",
            cbo = 100, tcc = 0.9f, lcc = 0.9f,
        )
        val touchedMethod = PmdMethodMetrics(
            className = "A", signature = "m()", file = "A.java",
            cyclo = 1, cognitive = 2, npath = "1", ncss = 1, atfd = 0,
        )
        val untouchedMethod = touchedMethod.copy(file = "B.java", cognitive = 99)
        val touchedViolation = PmdViolation(
            file = "A.java", rule = "X", ruleSet = "y", priority = 3,
            beginLine = 1, endLine = 1, message = "", snippet = null,
        )
        val untouchedViolation = touchedViolation.copy(file = "B.java")

        val a = checkpoint(
            "a",
            ck = CkResult(perClass = listOf(touchedClass, untouchedClass)),
            pmd = pmdResult(
                methodMetrics = listOf(touchedMethod, untouchedMethod),
                violations = listOf(touchedViolation, untouchedViolation),
            ),
            touchedFiles = listOf("A.java"),
        )
        val b = checkpoint("b", ck = ckResult(ckClass(cbo = 6, tcc = 0.5f)), touchedFiles = listOf("A.java"))

        val result = DerivedMetricsRunner().run(listOf(a, b), emptyList(), emptyList())
        val d = result.main.getValue("a")

        // Untouched class/method/violation in B.java excluded.
        assertEquals(4.0, d.coupling)
        assertEquals(0.4, d.cohesion)
        assertEquals(2, d.cognitive)
        assertEquals(1, d.smells)
    }

    @Test
    fun `composite weights are uniform across the six sub-signals`() {
        val readabilityFile = fileReadability(file = "A.java", totalLines = 100)
        val readabilityFileB = fileReadability(file = "A.java", totalLines = 100, avgLineLength = 90.0)
        val a = checkpoint(
            "a",
            ck = ckResult(ckClass(cbo = 1, tcc = 0.9f).copy(file = "A.java", className = "A")),
            cpd = CpdResult.EMPTY.copy(
                duplications = listOf(
                    CpdDuplication(
                        tokens = 0, lines = 10,
                        occurrences = listOf(CpdOccurrence(file = "A.java", beginLine = 1, endLine = 10)),
                    ),
                ),
            ),
            readability = ReadabilityResult.EMPTY.copy(perFile = listOf(readabilityFile)),
            pmd = pmdResult(
                methodMetrics = listOf(methodMetric(cognitive = 1).copy(file = "A.java")),
                violations = listOf(violation(priority = 1).copy(file = "A.java")),
            ),
            touchedFiles = listOf("A.java"),
        )
        val b = checkpoint(
            "b",
            ck = ckResult(ckClass(cbo = 5, tcc = 0.2f).copy(file = "A.java", className = "A")),
            cpd = CpdResult.EMPTY.copy(
                duplications = listOf(
                    CpdDuplication(
                        tokens = 0, lines = 40,
                        occurrences = listOf(CpdOccurrence(file = "A.java", beginLine = 1, endLine = 40)),
                    ),
                ),
            ),
            readability = ReadabilityResult.EMPTY.copy(perFile = listOf(readabilityFileB)),
            pmd = pmdResult(
                methodMetrics = listOf(methodMetric(cognitive = 9).copy(file = "A.java")),
                violations = listOf(
                    violation(priority = 1).copy(file = "A.java"),
                    violation(priority = 5).copy(file = "A.java"),
                ),
            ),
            touchedFiles = listOf("A.java"),
        )
        val result = DerivedMetricsRunner().run(listOf(a, b), emptyList(), emptyList())
        val cleanliness = result.main.getValue("a").cleanliness
        assertNotNull(cleanliness)
        assertEquals(6, cleanliness.contributions.size)
        assertTrue(
            cleanliness.contributions.all { it.weight == 1.0 },
            "every sub-signal should carry uniform weight=1.0; got ${cleanliness.contributions.map { it.id to it.weight }}",
        )
    }

    @Test
    fun `duplication is touched-file rate, not whole-codebase share`() {
        val dup = CpdDuplication(
            tokens = 0, lines = 10,
            occurrences = listOf(
                CpdOccurrence(file = "A.java", beginLine = 1, endLine = 10),
                CpdOccurrence(file = "B.java", beginLine = 1, endLine = 10),
            ),
        )
        val a = checkpoint(
            "a",
            ck = ckResult(ckClass(cbo = 1, tcc = 0.9f).copy(file = "A.java", className = "A")),
            cpd = CpdResult.EMPTY.copy(duplications = listOf(dup)),
            readability = ReadabilityResult.EMPTY.copy(
                perFile = listOf(
                    fileReadability(file = "A.java", totalLines = 100),
                    fileReadability(file = "B.java", totalLines = 9999),
                ),
            ),
            touchedFiles = listOf("A.java"),
        )
        val result = DerivedMetricsRunner().run(listOf(a), emptyList(), emptyList())
        assertEquals(10.0, result.main.getValue("a").duplication)
    }

    @Test
    fun `readability is a uniform 0_20 blend with line-count weighting across touched files`() {
        val a = fileReadability(
            file = "A.java",
            totalLines = 100,
            avgLineLength = 40.0,
            avgIndentation = 2.0,
            avgIdentifierLength = 10.0,
            singleLetterRatio = 0.1,
            dictionaryWordRatio = 0.5,
        )
        val b = fileReadability(
            file = "B.java",
            totalLines = 300,
            avgLineLength = 80.0,
            avgIndentation = 6.0,
            avgIdentifierLength = 5.0,
            singleLetterRatio = 0.3,
            dictionaryWordRatio = 0.9,
        )

        val cp = checkpoint(
            sha = "a",
            readability = ReadabilityResult.EMPTY.copy(perFile = listOf(a, b)),
        )

        val result = DerivedMetricsRunner().run(listOf(cp), emptyList(), emptyList())
        assertEquals(68.7, result.main.getValue("a").readability)
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
        touchedFiles: List<String>? = null,
    ): CheckpointReport {
        val derivedTouched = touchedFiles
            ?: (ck.perClass.map { it.file } +
                pmd.violations.map { it.file } +
                pmd.methodMetrics.map { it.file } +
                readability.perFile.map { it.file }).distinct()
        val diff = DiffStats.ZERO.copy(
            perFileChurn = derivedTouched.map { PerFileChurn(path = it, linesAdded = 1, linesDeleted = 0) },
        )
        return CheckpointReport(
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
            diff = diff,
            pmdTracking = pmdTracking,
        )
    }

    private fun syntheticEvent(timestamp: Long): EventSummary = EventSummary(
        id = "synthetic-$timestamp",
        type = EventType.EDIT_BURST,
        timestamp = timestamp,
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

    private fun fileReadability(
        file: String,
        totalLines: Int = 100,
        avgLineLength: Double = 60.0,
        avgIndentation: Double = 4.0,
        avgIdentifierLength: Double = 6.0,
        singleLetterRatio: Double = 0.05,
        dictionaryWordRatio: Double = 0.85,
    ): FileReadability = FileReadability(
        file = file,
        totalLines = totalLines,
        codeLines = totalLines,
        commentLines = 0,
        blankLines = 0,
        commentLineRatio = 0.0,
        blankLineRatio = 0.0,
        avgLineLength = avgLineLength,
        maxLineLength = avgLineLength.toInt(),
        avgIndentation = avgIndentation,
        maxIndentation = avgIndentation.toInt(),
        identifiers = IdentifierStats(
            count = 1,
            avgLength = avgIdentifierLength,
            singleLetterRatio = singleLetterRatio,
            avgWordCount = 1.0,
            dictionaryWordRatio = dictionaryWordRatio,
        ),
    )
}
