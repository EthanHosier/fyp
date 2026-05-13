package com.github.ethanhosier.analysis.diffs

import com.github.ethanhosier.analysis.alternative.rework.ReworkDriftTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PatchLineRenumberTest {

    @Test
    fun `identity when tracker has no zones`() {
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,3 +10,3 @@\n" +
            " a\n" +
            "-x\n" +
            "+X\n" +
            " b\n"

        val tracker = ReworkDriftTracker()
        assertEquals(patch, PatchLineRenumber.renumber(patch, tracker))
    }

    @Test
    fun `shifts modification hunk past added zone`() {
        // Zone covers user lines 5..7 (ADDED, length 3). Mod hunk at
        // user oldStart=20 → synth 17.
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -20,1 +20,1 @@\n" +
            "-x\n" +
            "+X\n"

        val tracker = ReworkDriftTracker()
        tracker.registerAddedZone("Foo.java", userStartLine = 5, length = 3)

        val expected =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -17,1 +17,1 @@\n" +
            "-x\n" +
            "+X\n"
        assertEquals(expected, PatchLineRenumber.renumber(patch, tracker))
    }

    @Test
    fun `pure insertion at end of added zone translates to synth boundary`() {
        // Zone covers user lines 3..8 (length 6). Insertion `@@ -8,0 +9,1 @@`
        // means "insert after user line 8" — at the zone's end. In
        // synth, this is "insert before line 3" → `@@ -2,0 +3,1 @@`.
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -8,0 +9,1 @@\n" +
            "+x\n"

        val tracker = ReworkDriftTracker()
        tracker.registerAddedZone("Foo.java", userStartLine = 3, length = 6)

        val expected =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -2,0 +3,1 @@\n" +
            "+x\n"
        assertEquals(expected, PatchLineRenumber.renumber(patch, tracker))
    }

    @Test
    fun `modification hunk targeting added-zone interior rejects patch`() {
        // Zone 5..7. Hunk `@@ -6,1 +6,1 @@` targets line 6 — interior.
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -6,1 +6,1 @@\n" +
            "-a\n" +
            "+b\n"

        val tracker = ReworkDriftTracker()
        tracker.registerAddedZone("Foo.java", userStartLine = 5, length = 3)

        assertNull(PatchLineRenumber.renumber(patch, tracker))
    }

    @Test
    fun `body preserved byte for byte`() {
        // Non-trivial body — content lines should round-trip exactly.
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -20,4 +20,4 @@\n" +
            " ctx_before\n" +
            "-removed line with trailing space \n" +
            "+    added with leading whitespace\n" +
            " ctx_between\n" +
            " ctx_after\n"

        val tracker = ReworkDriftTracker()
        tracker.registerAddedZone("Foo.java", userStartLine = 5, length = 3)

        val expected =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -17,4 +17,4 @@\n" +
            " ctx_before\n" +
            "-removed line with trailing space \n" +
            "+    added with leading whitespace\n" +
            " ctx_between\n" +
            " ctx_after\n"
        assertEquals(expected, PatchLineRenumber.renumber(patch, tracker))
    }

    @Test
    fun `removed zone shifts modification hunk up`() {
        // REMOVED zone at user 20.. length 4 → synth has 4 more lines
        // from line 20 onward. User line 25 → synth 29.
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -25,1 +25,1 @@\n" +
            "-x\n" +
            "+X\n"

        val tracker = ReworkDriftTracker()
        tracker.registerRemovedZone("Foo.java", userStartLine = 20, length = 4)

        val expected =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -29,1 +29,1 @@\n" +
            "-x\n" +
            "+X\n"
        assertEquals(expected, PatchLineRenumber.renumber(patch, tracker))
    }

    @Test
    fun `multi-hunk patch translates each hunk and preserves intra-patch delta`() {
        // Two hunks in user coords:
        //   @@ -10,3 +10,5 @@  (mod, +2 net)
        //   @@ -20,1 +22,1 @@  (mod, no net change; newStart=22 reflects prior +2)
        // Zone: ADDED 5..7 (length 3) → drift -3 for everything past line 7.
        // Hunk 1: oldStart 10 → 7, newStart 10 → 7
        // Hunk 2: oldStart 20 → 17, newStart 22 → 19 (= 17 + (22-20))
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -10,3 +10,5 @@\n" +
            " a\n" +
            "+x\n" +
            "+y\n" +
            " b\n" +
            " c\n" +
            "@@ -20,1 +22,1 @@\n" +
            "-p\n" +
            "+P\n"

        val tracker = ReworkDriftTracker()
        tracker.registerAddedZone("Foo.java", userStartLine = 5, length = 3)

        val expected =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -7,3 +7,5 @@\n" +
            " a\n" +
            "+x\n" +
            "+y\n" +
            " b\n" +
            " c\n" +
            "@@ -17,1 +19,1 @@\n" +
            "-p\n" +
            "+P\n"
        assertEquals(expected, PatchLineRenumber.renumber(patch, tracker))
    }

    @Test
    fun `zones in other files do not affect this file`() {
        val patch =
            "diff --git a/Bar.java b/Bar.java\n" +
            "--- a/Bar.java\n" +
            "+++ b/Bar.java\n" +
            "@@ -10,1 +10,1 @@\n" +
            "-x\n" +
            "+X\n"

        val tracker = ReworkDriftTracker()
        tracker.registerAddedZone("Foo.java", userStartLine = 5, length = 3)

        assertEquals(patch, PatchLineRenumber.renumber(patch, tracker))
    }

    @Test
    fun `pure insertion before zone uses direct translation`() {
        // Zone 5..7. Insertion `@@ -4,0 +5,1 @@` — insert after user
        // line 4 (just before zone). Direct translate(4) = 4, so synth
        // header `@@ -4,0 +5,1 @@`.
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,0 +5,1 @@\n" +
            "+new\n"

        val tracker = ReworkDriftTracker()
        tracker.registerAddedZone("Foo.java", userStartLine = 5, length = 3)

        val expected =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,0 +5,1 @@\n" +
            "+new\n"
        assertEquals(expected, PatchLineRenumber.renumber(patch, tracker))
    }

    @Test
    fun `section heading suffix preserved in renumbered header`() {
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -20,1 +20,1 @@ public void run()\n" +
            "-x\n" +
            "+X\n"

        val tracker = ReworkDriftTracker()
        tracker.registerAddedZone("Foo.java", userStartLine = 5, length = 3)

        val expected =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -17,1 +17,1 @@ public void run()\n" +
            "-x\n" +
            "+X\n"
        assertEquals(expected, PatchLineRenumber.renumber(patch, tracker))
    }

    @Test
    fun `multi-file patch with one untranslatable hunk rejects whole patch`() {
        // First file's hunk is fine; second file's hunk targets a
        // zone interior → whole patch rejected (null).
        val patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -20,1 +20,1 @@\n" +
            "-a\n" +
            "+A\n" +
            "diff --git a/Bar.java b/Bar.java\n" +
            "--- a/Bar.java\n" +
            "+++ b/Bar.java\n" +
            "@@ -6,1 +6,1 @@\n" +
            "-b\n" +
            "+B\n"

        val tracker = ReworkDriftTracker()
        tracker.registerAddedZone("Bar.java", userStartLine = 5, length = 3)

        assertNull(PatchLineRenumber.renumber(patch, tracker))
    }
}
