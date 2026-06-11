package com.github.ethanhosier.analysis.alternative.rework

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReworkDriftTrackerTest {

    @Test
    fun `no zones translates identity`() {
        val t = ReworkDriftTracker()
        assertEquals(0, t.translate("Foo.java", 0))
        assertEquals(1, t.translate("Foo.java", 1))
        assertEquals(50, t.translate("Foo.java", 50))
    }

    @Test
    fun `added zone before zone is identity, inside is null, after is shifted`() {
        // Zone covers user lines 5..7 (length 3). Synth doesn't have
        // these lines. User line 8 → synth line 5.
        val t = ReworkDriftTracker()
        t.registerAddedZone("Foo.java", userStartLine = 5, length = 3)

        assertEquals(4, t.translate("Foo.java", 4))
        assertNull(t.translate("Foo.java", 5))
        assertNull(t.translate("Foo.java", 6))
        assertNull(t.translate("Foo.java", 7))
        assertEquals(5, t.translate("Foo.java", 8))
        assertEquals(47, t.translate("Foo.java", 50))
    }

    @Test
    fun `removed zone shifts userStartLine onward up by length`() {
        // Zone: user removed 3 lines starting at userStartLine=5.
        // Synth still has those 3 lines, so user line 5 → synth line 8.
        val t = ReworkDriftTracker()
        t.registerRemovedZone("Foo.java", userStartLine = 5, length = 3)

        assertEquals(4, t.translate("Foo.java", 4))
        assertEquals(8, t.translate("Foo.java", 5))
        assertEquals(9, t.translate("Foo.java", 6))
        assertEquals(53, t.translate("Foo.java", 50))
    }

    @Test
    fun `zones are scoped per file`() {
        val t = ReworkDriftTracker()
        t.registerAddedZone("Foo.java", userStartLine = 5, length = 3)

        // Bar.java has no zones → identity.
        assertEquals(5, t.translate("Bar.java", 5))
        assertEquals(10, t.translate("Bar.java", 10))
    }

    @Test
    fun `multiple zones compose by cumulative offset`() {
        // ADDED zone 5..7 (delta -3 past zone)
        // ADDED zone 20..21 (delta -2 past zone)
        val t = ReworkDriftTracker()
        t.registerAddedZone("Foo.java", userStartLine = 5, length = 3)
        t.registerAddedZone("Foo.java", userStartLine = 20, length = 2)

        assertEquals(4, t.translate("Foo.java", 4))
        assertNull(t.translate("Foo.java", 6))
        assertEquals(5, t.translate("Foo.java", 8))   // -3
        assertEquals(16, t.translate("Foo.java", 19))  // still only -3
        assertNull(t.translate("Foo.java", 20))
        assertNull(t.translate("Foo.java", 21))
        assertEquals(17, t.translate("Foo.java", 22)) // -3 -2 = -5
    }

    @Test
    fun `mixed ADDED and REMOVED zones compose correctly`() {
        // ADDED zone at 5..7 (-3)
        // REMOVED zone at 20.. (+4 from 20 onward)
        val t = ReworkDriftTracker()
        t.registerAddedZone("Foo.java", userStartLine = 5, length = 3)
        t.registerRemovedZone("Foo.java", userStartLine = 20, length = 4)

        assertEquals(21, t.translate("Foo.java", 20)) // -3 +4 = +1
        assertEquals(22, t.translate("Foo.java", 21))
        assertEquals(23, t.translate("Foo.java", 22))
    }

    @Test
    fun `recordHunkApplied shifts later zones by net delta`() {
        // ADDED zone at 50..52. Insert 2 lines at user oldStart=20 (oldLen=1, newLen=3 → net +2).
        // Zone should re-anchor to userStartLine=52.
        val t = ReworkDriftTracker()
        t.registerAddedZone("Foo.java", userStartLine = 50, length = 3)
        t.recordHunkApplied("Foo.java", userOldStart = 20, oldLen = 1, newLen = 3)

        // After re-anchoring, translate(52) should be the start of the zone (=null).
        assertNull(t.translate("Foo.java", 52))
        // Pre-zone identity preserved for the new line numbering.
        assertEquals(51, t.translate("Foo.java", 51))
        // Past-zone offset -3.
        assertEquals(52, t.translate("Foo.java", 55))
    }

    @Test
    fun `recordHunkApplied does not shift zones before the hunk`() {
        // Zone at 5..7. Hunk applies at user line 50 with net +2. Zone unaffected.
        val t = ReworkDriftTracker()
        t.registerAddedZone("Foo.java", userStartLine = 5, length = 3)
        t.recordHunkApplied("Foo.java", userOldStart = 50, oldLen = 1, newLen = 3)

        assertNull(t.translate("Foo.java", 5))   // still interior
        assertNull(t.translate("Foo.java", 7))
        assertEquals(5, t.translate("Foo.java", 8))
    }

    @Test
    fun `recordHunkApplied with zero net delta is a no-op`() {
        val t = ReworkDriftTracker()
        t.registerAddedZone("Foo.java", userStartLine = 5, length = 3)
        val before = t.zoneSnapshot()

        t.recordHunkApplied("Foo.java", userOldStart = 1, oldLen = 1, newLen = 1)
        assertEquals(before, t.zoneSnapshot())
    }

    @Test
    fun `clearing a zone restores identity past its old position`() {
        val t = ReworkDriftTracker()
        val id = t.registerAddedZone("Foo.java", userStartLine = 5, length = 3)

        assertEquals(5, t.translate("Foo.java", 8))

        t.clearZone(id)

        assertEquals(5, t.translate("Foo.java", 5))
        assertEquals(8, t.translate("Foo.java", 8))
    }

    @Test
    fun `translateInsertionPoint at end of added zone scans forward`() {
        // Zone covers user lines 3..8 (length 6). Insertion "after
        // user line 8" → user line 9 → synth 9 - 6 = 3; minus 1 → 2.
        val t = ReworkDriftTracker()
        t.registerAddedZone("Foo.java", userStartLine = 3, length = 6)

        assertEquals(2, t.translateInsertionPoint("Foo.java", userOldStart = 8))
    }

    @Test
    fun `translateInsertionPoint before added zone uses direct translation`() {
        // Zone 5..7. Insertion after user line 4 → translate(4) = 4 directly.
        val t = ReworkDriftTracker()
        t.registerAddedZone("Foo.java", userStartLine = 5, length = 3)

        assertEquals(4, t.translateInsertionPoint("Foo.java", userOldStart = 4))
    }

    @Test
    fun `translateInsertionPoint at file start with no zones is zero`() {
        val t = ReworkDriftTracker()
        assertEquals(0, t.translateInsertionPoint("Foo.java", userOldStart = 0))
    }

    @Test
    fun `translateInsertionPoint inside zone interior also scans forward`() {
        val t = ReworkDriftTracker()
        t.registerAddedZone("Foo.java", userStartLine = 3, length = 6)

        assertEquals(2, t.translateInsertionPoint("Foo.java", userOldStart = 5))
    }
}
