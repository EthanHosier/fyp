package com.github.ethanhosier.analysis.metrics.cpd

import com.github.ethanhosier.analysis.metrics.model.CpdTracking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CpdDuplicationTrackerTest {

    @Test
    fun `seed stamps every group with the seed sha`() {
        val cpd = cpdResult(
            dup("id-A", listOf(occ("X.java", 1, 5))),
            dup("id-B", listOf(occ("Y.java", 10, 15))),
        )
        val tracking = CpdDuplicationTracker.seed("sha0", cpd)
        assertEquals(listOf("sha0", "sha0"), tracking.firstSeenAtSha)
        assertTrue(tracking.resolvedSincePrev.isEmpty())
    }

    @Test
    fun `carried groups inherit the prev firstSeenAtSha across hops`() {
        val cpdSeed = cpdResult(dup("id-A", listOf(occ("X.java", 1, 5))))
        val cpdMid = cpdResult(dup("id-A", listOf(occ("X.java", 1, 5))))
        val cpdNow = cpdResult(dup("id-A", listOf(occ("X.java", 8, 12))))

        val seedTracking = CpdDuplicationTracker.seed("sha0", cpdSeed)
        val midTracking = CpdDuplicationTracker.track(
            currSha = "sha1", prev = cpdSeed, curr = cpdMid, prevTracking = seedTracking,
        )
        val nowTracking = CpdDuplicationTracker.track(
            currSha = "sha2", prev = cpdMid, curr = cpdNow, prevTracking = midTracking,
        )
        // id-A originated at sha0; identity survives the line shift.
        assertEquals(listOf("sha0"), nowTracking.firstSeenAtSha)
        assertTrue(nowTracking.resolvedSincePrev.isEmpty())
    }

    @Test
    fun `new groups stamp the current sha and resolved groups land on the sidecar`() {
        val cpdPrev = cpdResult(
            dup("id-A", listOf(occ("X.java", 1, 5))),
            dup("id-B", listOf(occ("Y.java", 10, 15))),
        )
        // id-B vanished, id-C is new.
        val cpdCurr = cpdResult(
            dup("id-A", listOf(occ("X.java", 1, 5))),
            dup("id-C", listOf(occ("Z.java", 20, 25))),
        )
        val prevTracking = CpdTracking(firstSeenAtSha = listOf("sha-init", "sha-mid"))
        val tracking = CpdDuplicationTracker.track(
            currSha = "sha-now", prev = cpdPrev, curr = cpdCurr, prevTracking = prevTracking,
        )

        // id-A carried (inherits sha-init), id-C new (gets sha-now).
        assertEquals(listOf("sha-init", "sha-now"), tracking.firstSeenAtSha)
        assertEquals(1, tracking.resolvedSincePrev.size)
        val resolved = tracking.resolvedSincePrev.single()
        assertEquals("id-B", resolved.identity)
        assertEquals("sha-mid", resolved.firstSeenAtSha)
        assertEquals(listOf("Y.java"), resolved.prevOccurrences.map { it.file })
    }

    @Test
    fun `groups with empty identity are skipped from matching but still get a sha`() {
        val cpdPrev = cpdResult(dup("", listOf(occ("X.java", 1, 5))))
        val cpdCurr = cpdResult(dup("", listOf(occ("X.java", 1, 5))))
        val tracking = CpdDuplicationTracker.track(
            currSha = "sha2", prev = cpdPrev, curr = cpdCurr,
            prevTracking = CpdTracking(firstSeenAtSha = listOf("sha1")),
        )
        // No identity → no match → curr stamped with sha2, prev not resolved
        // (skipped from the resolved list since we can't fingerprint it).
        assertEquals(listOf("sha2"), tracking.firstSeenAtSha)
        assertTrue(tracking.resolvedSincePrev.isEmpty())
    }

    private fun dup(identity: String, occurrences: List<CpdOccurrence>) = CpdDuplication(
        tokens = 50,
        lines = occurrences.first().endLine - occurrences.first().beginLine + 1,
        occurrences = occurrences,
        identity = identity,
    )

    private fun occ(file: String, beginLine: Int, endLine: Int) = CpdOccurrence(
        file = file, beginLine = beginLine, endLine = endLine, snippet = null,
    )

    private fun cpdResult(vararg duplications: CpdDuplication): CpdResult = CpdResult(
        duplicationBlocks = duplications.size,
        duplicatedLines = duplications.sumOf { it.lines * it.occurrences.size },
        duplicatedTokens = duplications.sumOf { it.tokens * it.occurrences.size },
        totalLines = 100,
        totalTokens = 500,
        duplicatedLinesShare = 0.1,
        largestBlockLines = duplications.maxOfOrNull { it.lines } ?: 0,
        largestBlockTokens = duplications.maxOfOrNull { it.tokens } ?: 0,
        filesInvolvedInDuplication = duplications.flatMap { it.occurrences.map { o -> o.file } }.toSet().size,
        duplications = duplications.toList(),
    )
}
