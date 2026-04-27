package com.github.ethanhosier.analysis.metrics.pmd

import com.github.ethanhosier.analysis.metrics.model.PmdTracking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PmdViolationTrackerTest {

    private val rule = "AvoidLiteralsInIfCondition"
    private val ruleSet = "errorprone"

    private fun violation(
        file: String,
        line: Int,
        rule: String = this.rule,
    ) = PmdViolation(
        file = file,
        rule = rule,
        ruleSet = ruleSet,
        priority = 3,
        beginLine = line,
        endLine = line,
        message = "$rule@$file:$line",
        snippet = null,
    )

    private fun result(vararg violations: PmdViolation) = PmdResult(violations = violations.toList())

    @Test
    fun `seed stamps every violation with the seed sha and reports nothing resolved`() {
        val curr = result(violation("Foo.java", 10), violation("Foo.java", 20))
        val tracking = PmdViolationTracker.seed("seed", curr)

        assertEquals(listOf("seed", "seed"), tracking.firstSeenAtSha)
        assertTrue(tracking.resolvedSincePrev.isEmpty())
    }

    @Test
    fun `delete first of N adjacent same-rule flags reports exactly one resolved`() {
        // Five literal-if violations at lines 10..14 in A; user deletes
        // the first. PMD on B reports four violations at 10..13 (lines
        // shifted up). Tracker should match all four as carried with the
        // seed origin and surface the deleted A@10 as resolved.
        val seed = "A"
        val nextSha = "B"
        val prev = result(*(10..14).map { violation("Foo.java", it) }.toTypedArray())
        val curr = result(*(10..13).map { violation("Foo.java", it) }.toTypedArray())

        val deletionDiff = """
            @@ -10 +9,0 @@
            -    if (x == 0) return;
        """.trimIndent()
        val mapper = DiffLineMapper.parse(deletionDiff)

        val tracking = PmdViolationTracker.track(
            prevSha = seed,
            currSha = nextSha,
            prev = prev,
            curr = curr,
            prevTracking = PmdViolationTracker.seed(seed, prev),
            translatePath = { it },
            mapperFor = { mapper },
        )

        // All four curr violations should inherit `A` (the seed) — they
        // were first seen there even though we just transitioned A → B.
        assertEquals(listOf("A", "A", "A", "A"), tracking.firstSeenAtSha)
        assertEquals(1, tracking.resolvedSincePrev.size)
        val gone = tracking.resolvedSincePrev.single()
        assertEquals(rule, gone.rule)
        assertEquals(10, gone.prevBeginLine)
        assertEquals("A", gone.firstSeenAtSha)
    }

    @Test
    fun `delete the middle violation also reports one resolved`() {
        val prev = result(*(10..14).map { violation("Foo.java", it) }.toTypedArray())
        val curr = result(*listOf(10, 11, 12, 13).map { violation("Foo.java", it) }.toTypedArray())

        val mapper = DiffLineMapper.parse(
            """
            @@ -12 +11,0 @@
            -    if (x == 2) return;
            """.trimIndent()
        )

        val tracking = PmdViolationTracker.track(
            prevSha = "A", currSha = "B",
            prev = prev, curr = curr,
            prevTracking = PmdViolationTracker.seed("A", prev),
            translatePath = { it },
            mapperFor = { mapper },
        )

        assertEquals(1, tracking.resolvedSincePrev.size)
        assertEquals(12, tracking.resolvedSincePrev.single().prevBeginLine)
        assertEquals(listOf("A", "A", "A", "A"), tracking.firstSeenAtSha)
    }

    @Test
    fun `brand new violation gets the current sha and nothing resolved`() {
        val prev = result(violation("Foo.java", 10))
        val curr = result(violation("Foo.java", 10), violation("Foo.java", 30))

        val tracking = PmdViolationTracker.track(
            prevSha = "A", currSha = "B",
            prev = prev, curr = curr,
            prevTracking = PmdViolationTracker.seed("A", prev),
            translatePath = { it },
            mapperFor = { DiffLineMapper.IDENTITY },
        )

        assertEquals(listOf("A", "B"), tracking.firstSeenAtSha)
        assertTrue(tracking.resolvedSincePrev.isEmpty())
    }

    @Test
    fun `firstSeenAtSha is propagated across multiple hops`() {
        // c0 → c1 → c2: a violation that survives all three transitions
        // should still report c0 as its origin at c2, not c1.
        val v0 = violation("Foo.java", 10)
        val r0 = result(v0)
        val tracking0 = PmdViolationTracker.seed("c0", r0)

        val r1 = result(violation("Foo.java", 10))
        val tracking1 = PmdViolationTracker.track(
            prevSha = "c0", currSha = "c1",
            prev = r0, curr = r1,
            prevTracking = tracking0,
            translatePath = { it },
            mapperFor = { DiffLineMapper.IDENTITY },
        )
        assertEquals(listOf("c0"), tracking1.firstSeenAtSha)

        val r2 = result(violation("Foo.java", 10))
        val tracking2 = PmdViolationTracker.track(
            prevSha = "c1", currSha = "c2",
            prev = r1, curr = r2,
            prevTracking = tracking1,
            translatePath = { it },
            mapperFor = { DiffLineMapper.IDENTITY },
        )
        assertEquals(listOf("c0"), tracking2.firstSeenAtSha)
    }

    @Test
    fun `file deletion sends every prev violation in that file to resolved`() {
        val prev = result(violation("Foo.java", 10), violation("Foo.java", 20))
        val curr = result()

        val tracking = PmdViolationTracker.track(
            prevSha = "A", currSha = "B",
            prev = prev, curr = curr,
            prevTracking = PmdViolationTracker.seed("A", prev),
            translatePath = { null },                       // file deleted
            mapperFor = { error("must not be called when path is deleted") },
        )

        assertTrue(tracking.firstSeenAtSha.isEmpty())
        assertEquals(2, tracking.resolvedSincePrev.size)
        assertTrue(tracking.resolvedSincePrev.all { it.firstSeenAtSha == "A" })
    }

    @Test
    fun `path rename routes prev violation onto the new file`() {
        val prev = result(violation("OldName.java", 42))
        val curr = result(violation("NewName.java", 42))

        val tracking = PmdViolationTracker.track(
            prevSha = "A", currSha = "B",
            prev = prev, curr = curr,
            prevTracking = PmdViolationTracker.seed("A", prev),
            translatePath = { if (it == "OldName.java") "NewName.java" else it },
            mapperFor = { DiffLineMapper.IDENTITY },
        )

        assertEquals(listOf("A"), tracking.firstSeenAtSha)
        assertTrue(tracking.resolvedSincePrev.isEmpty())
    }

    @Test
    fun `equal-count hunk lets in-place edits stay carried`() {
        // Single-line in-place edit at line 5: still flagged by the same
        // rule on the new side, line stays 5. The mapper pairs them 1:1
        // and the tracker should treat it as carried.
        val prev = result(violation("Foo.java", 5))
        val curr = result(violation("Foo.java", 5))
        val mapper = DiffLineMapper.parse(
            """
            @@ -5,1 +5,1 @@
            -    if (x == 0) return;
            +    if (x == LIMIT) return;
            """.trimIndent()
        )

        val tracking = PmdViolationTracker.track(
            prevSha = "A", currSha = "B",
            prev = prev, curr = curr,
            prevTracking = PmdViolationTracker.seed("A", prev),
            translatePath = { it },
            mapperFor = { mapper },
        )
        assertEquals(listOf("A"), tracking.firstSeenAtSha)
        assertTrue(tracking.resolvedSincePrev.isEmpty())
    }

    @Test
    fun `line tolerance absorbs PMD jitter of one`() {
        // Same logical violation but PMD reports it one line earlier on
        // the new side (e.g. binds to a different AST node).
        val prev = result(violation("Foo.java", 10))
        val curr = result(violation("Foo.java", 9))

        val tracking = PmdViolationTracker.track(
            prevSha = "A", currSha = "B",
            prev = prev, curr = curr,
            prevTracking = PmdViolationTracker.seed("A", prev),
            translatePath = { it },
            mapperFor = { DiffLineMapper.IDENTITY },
        )
        assertEquals(listOf("A"), tracking.firstSeenAtSha)
        assertTrue(tracking.resolvedSincePrev.isEmpty())
    }
}
