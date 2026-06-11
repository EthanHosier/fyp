package com.github.ethanhosier.analysis.alternative.rework

import com.github.ethanhosier.analysis.alternative.rework.ReworkAlternativeBuilder.ChunkPair
import com.github.ethanhosier.analysis.alternative.rework.ReworkAlternativeBuilder.Direction
import com.github.ethanhosier.analysis.alternative.rework.ReworkAlternativeBuilder.PatchApplier
import com.github.ethanhosier.analysis.alternative.rework.ReworkAlternativeBuilder.StepInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class ReworkAlternativeBuilderTest {

    @Test
    fun `clean surgery succeeds -- both steps surgered to empty`() {
        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,3 @@\n" +
            "+int a = 1;\n" +
            "+int b = 2;\n" +
            "+int c = 3;\n"

        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,3 +3,0 @@\n" +
            "-int a = 1;\n" +
            "-int b = 2;\n" +
            "-int c = 3;\n"

        val pair = ChunkPair(
            originatingStep = 1, terminalStep = 3, file = "Foo.java",
            direction = Direction.ADD_THEN_REMOVE,
            originatingRunStartLine = 4, terminalRunStartLine = 4, rawLineCount = 3,
        )

        val plan = ReworkAlternativeBuilder.plan(pair, listOf(
            StepInput(1, step1Patch),
            StepInput(3, step3Patch),
        ))
        assertNotNull(plan)
        assertEquals(2, plan.steps.size)
        assertEquals("", plan.steps[0].patch)
        assertEquals("", plan.steps[1].patch)
    }

    @Test
    fun `intervening edit far from zone renumbers and survives`() {
        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,3 @@\n" +
            "+a\n" +
            "+b\n" +
            "+c\n"

        // Step 2 in user coords: line 50 modified.
        val step2Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -50,1 +50,1 @@\n" +
            "-old\n" +
            "+new\n"

        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,3 +3,0 @@\n" +
            "-a\n" +
            "-b\n" +
            "-c\n"

        val pair = ChunkPair(
            originatingStep = 1, terminalStep = 3, file = "Foo.java",
            direction = Direction.ADD_THEN_REMOVE,
            originatingRunStartLine = 4, terminalRunStartLine = 4, rawLineCount = 3,
        )

        val plan = ReworkAlternativeBuilder.plan(pair, listOf(
            StepInput(1, step1Patch),
            StepInput(2, step2Patch),
            StepInput(3, step3Patch),
        ))
        assertNotNull(plan)
        assertEquals(3, plan.steps.size)

        // Step 1 surgered → empty.
        assertEquals("", plan.steps[0].patch)

        // Step 2 renumbered: user line 50 → synth 47 (zone -3).
        val expectedStep2 =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -47,1 +47,1 @@\n" +
            "-old\n" +
            "+new\n"
        assertEquals(expectedStep2, plan.steps[1].patch)

        // Step 3 surgered → empty (terminal surgery removes the -run).
        assertEquals("", plan.steps[2].patch)
    }

    @Test
    fun `intermediate edit inside zone aborts the plan`() {
        // Step 1 adds 3 lines (zone 4..6). Step 2 modifies user line 5
        // — inside the zone. Plan should return null.
        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,3 @@\n" +
            "+a\n" +
            "+b\n" +
            "+c\n"

        val step2Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -5,1 +5,1 @@\n" +
            "-b\n" +
            "+B\n"

        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,3 +3,0 @@\n" +
            "-a\n" +
            "-B\n" +
            "-c\n"

        val pair = ChunkPair(
            originatingStep = 1, terminalStep = 3, file = "Foo.java",
            direction = Direction.ADD_THEN_REMOVE,
            originatingRunStartLine = 4, terminalRunStartLine = 4, rawLineCount = 3,
        )

        val plan = ReworkAlternativeBuilder.plan(pair, listOf(
            StepInput(1, step1Patch),
            StepInput(2, step2Patch),
            StepInput(3, step3Patch),
        ))
        assertNull(plan)
    }

    @Test
    fun `insertion at end of zone is renumbered to synth boundary`() {
        // Zone covers user 4..6. Step 2 inserts after user line 6
        // (right at zone end) → synth header should be `@@ -3,0 +4,1 @@`.
        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,3 @@\n" +
            "+a\n" +
            "+b\n" +
            "+c\n"

        val step2Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -6,0 +7,1 @@\n" +
            "+inserted\n"

        // Step 3 removes the chunk; but note user tree has shifted —
        // the chunk's lines are still at user 4..6 (insertion was after).
        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,3 +3,0 @@\n" +
            "-a\n" +
            "-b\n" +
            "-c\n"

        val pair = ChunkPair(
            originatingStep = 1, terminalStep = 3, file = "Foo.java",
            direction = Direction.ADD_THEN_REMOVE,
            originatingRunStartLine = 4, terminalRunStartLine = 4, rawLineCount = 3,
        )

        val plan = ReworkAlternativeBuilder.plan(pair, listOf(
            StepInput(1, step1Patch),
            StepInput(2, step2Patch),
            StepInput(3, step3Patch),
        ))
        assertNotNull(plan)

        val expectedStep2 =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,1 @@\n" +
            "+inserted\n"
        assertEquals(expectedStep2, plan.steps[1].patch)
    }

    @Test
    fun `remove-then-add direction works symmetrically`() {
        // Step 1 removes 2 lines; step 3 adds them back.
        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,2 +3,0 @@\n" +
            "-a\n" +
            "-b\n"

        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,2 @@\n" +
            "+a\n" +
            "+b\n"

        val pair = ChunkPair(
            originatingStep = 1, terminalStep = 3, file = "Foo.java",
            direction = Direction.REMOVE_THEN_ADD,
            originatingRunStartLine = 4, terminalRunStartLine = 4, rawLineCount = 2,
        )

        val plan = ReworkAlternativeBuilder.plan(pair, listOf(
            StepInput(1, step1Patch),
            StepInput(3, step3Patch),
        ))
        assertNotNull(plan)
        assertEquals("", plan.steps[0].patch)
        assertEquals("", plan.steps[1].patch)
    }

    @Test
    fun `build returns null if applier rejects any patch`() {
        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,1 @@\n" +
            "+a\n"

        // Non-empty intermediate patch so the applier sees a non-skip step.
        val step2Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -50,1 +50,1 @@\n" +
            "-x\n" +
            "+X\n"

        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,1 +3,0 @@\n" +
            "-a\n"

        val pair = ChunkPair(
            originatingStep = 1, terminalStep = 3, file = "Foo.java",
            direction = Direction.ADD_THEN_REMOVE,
            originatingRunStartLine = 4, terminalRunStartLine = 4, rawLineCount = 1,
        )

        val rejectingApplier = PatchApplier { _, _ -> false }
        val out = ReworkAlternativeBuilder.build(pair, listOf(
            StepInput(1, step1Patch),
            StepInput(2, step2Patch),
            StepInput(3, step3Patch),
        ), rejectingApplier)
        assertNull(out)
    }

    @Test
    fun `build records every non-empty patch in apply order`() {
        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,1 @@\n" +
            "+a\n"

        val step2Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -50,1 +50,1 @@\n" +
            "-x\n" +
            "+X\n"

        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,1 +3,0 @@\n" +
            "-a\n"

        val pair = ChunkPair(
            originatingStep = 1, terminalStep = 3, file = "Foo.java",
            direction = Direction.ADD_THEN_REMOVE,
            originatingRunStartLine = 4, terminalRunStartLine = 4, rawLineCount = 1,
        )

        val applied = mutableListOf<Int>()
        val applier = PatchApplier { stepIndex, _ ->
            applied += stepIndex
            true
        }
        val plan = ReworkAlternativeBuilder.build(pair, listOf(
            StepInput(1, step1Patch),
            StepInput(2, step2Patch),
            StepInput(3, step3Patch),
        ), applier)
        assertNotNull(plan)
        // Only the non-empty step 2 reaches the applier in this case.
        assertEquals(listOf(2), applied)
    }

    @Test
    fun `REMOVE_THEN_ADD with kept plus-run anchors zone past the kept run`() {
        val step0Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -31,17 +31,1 @@\n" +
            "-l1\n" + "-l2\n" + "-l3\n" + "-l4\n" + "-l5\n" +
            "-l6\n" + "-l7\n" + "-l8\n" + "-l9\n" + "-l10\n" +
            "-l11\n" + "-l12\n" + "-l13\n" + "-l14\n" + "-l15\n" +
            "-l16\n" + "-l17\n" +
            "+blank\n"

        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -31,1 +31,1 @@\n" +
            "-blank\n" +
            "+BLANK\n"

        val step2Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -31,1 +31,17 @@\n" +
            "-BLANK\n" +
            "+l1\n" + "+l2\n" + "+l3\n" + "+l4\n" + "+l5\n" +
            "+l6\n" + "+l7\n" + "+l8\n" + "+l9\n" + "+l10\n" +
            "+l11\n" + "+l12\n" + "+l13\n" + "+l14\n" + "+l15\n" +
            "+l16\n" + "+l17\n"

        val pair = ChunkPair(
            originatingStep = 0, terminalStep = 2, file = "Foo.java",
            direction = Direction.REMOVE_THEN_ADD,
            originatingRunStartLine = 31, terminalRunStartLine = 31, rawLineCount = 17,
        )

        val plan = ReworkAlternativeBuilder.plan(pair, listOf(
            StepInput(0, step0Patch),
            StepInput(1, step1Patch),
            StepInput(2, step2Patch),
        ))
        assertNotNull(plan)
        assertEquals(3, plan.steps.size)

        // Step 0 surgered → pure-insertion of the +blank just before
        // pre-line 31 (with the off-by-one fix for modification→insertion).
        val expectedStep0 =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -30,0 +31,1 @@\n" +
            "+blank\n"
        assertEquals(expectedStep0, plan.steps[0].patch)

        assertEquals(step1Patch, plan.steps[1].patch)

        val expectedStep2 =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -31,1 +30,0 @@\n" +
            "-BLANK\n"
        assertEquals(expectedStep2, plan.steps[2].patch)
    }

    @Test
    fun `REMOVE_THEN_ADD intermediate step past the kept plus-run translates by zone length`() {
        val step0Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -31,17 +31,1 @@\n" +
            "-l1\n" + "-l2\n" + "-l3\n" + "-l4\n" + "-l5\n" +
            "-l6\n" + "-l7\n" + "-l8\n" + "-l9\n" + "-l10\n" +
            "-l11\n" + "-l12\n" + "-l13\n" + "-l14\n" + "-l15\n" +
            "-l16\n" + "-l17\n" +
            "+blank\n"

        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -50,1 +50,1 @@\n" +
            "-old\n" +
            "+new\n"

        val step2Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -31,1 +31,17 @@\n" +
            "-BLANK\n" +
            "+l1\n" + "+l2\n" + "+l3\n" + "+l4\n" + "+l5\n" +
            "+l6\n" + "+l7\n" + "+l8\n" + "+l9\n" + "+l10\n" +
            "+l11\n" + "+l12\n" + "+l13\n" + "+l14\n" + "+l15\n" +
            "+l16\n" + "+l17\n"

        val pair = ChunkPair(
            originatingStep = 0, terminalStep = 2, file = "Foo.java",
            direction = Direction.REMOVE_THEN_ADD,
            originatingRunStartLine = 31, terminalRunStartLine = 31, rawLineCount = 17,
        )

        val plan = ReworkAlternativeBuilder.plan(pair, listOf(
            StepInput(0, step0Patch),
            StepInput(1, step1Patch),
            StepInput(2, step2Patch),
        ))
        assertNotNull(plan)

        // user line 50 → synth line 50 + 17 = 67.
        val expectedStep1 =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -67,1 +67,1 @@\n" +
            "-old\n" +
            "+new\n"
        assertEquals(expectedStep1, plan.steps[1].patch)
    }

    @Test
    fun `plan extends past terminal step with cleared zone`() {
        // Step 1 add, step 3 remove (terminal). Step 4 modifies far line.
        // Plan should include step 4 with identity translation (zone cleared).
        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,1 @@\n" +
            "+a\n"

        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,1 +3,0 @@\n" +
            "-a\n"

        val step4Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,1 +10,1 @@\n" +
            "-x\n" +
            "+X\n"

        val pair = ChunkPair(
            originatingStep = 1, terminalStep = 3, file = "Foo.java",
            direction = Direction.ADD_THEN_REMOVE,
            originatingRunStartLine = 4, terminalRunStartLine = 4, rawLineCount = 1,
        )

        val plan = ReworkAlternativeBuilder.plan(pair, listOf(
            StepInput(1, step1Patch),
            StepInput(3, step3Patch),
            StepInput(4, step4Patch),
        ))
        assertNotNull(plan)
        assertEquals(listOf(1, 3, 4), plan.steps.map { it.stepIndex })
        // Step 4 unchanged (zone cleared, identity translation).
        assertEquals(step4Patch, plan.steps[2].patch)
    }
}
