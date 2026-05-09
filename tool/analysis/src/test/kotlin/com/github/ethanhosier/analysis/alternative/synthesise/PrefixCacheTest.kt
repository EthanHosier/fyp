package com.github.ethanhosier.analysis.alternative.synthesise

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PrefixCacheTest {

    @Test
    fun `empty cache returns fromSha for any ordering`() {
        val cache = PrefixCache(fromSha = "BASE")
        val (prefix, sha) = cache.deepestHit(listOf(2, 0, 1))
        assertEquals(emptyList(), prefix)
        assertEquals("BASE", sha)
    }

    @Test
    fun `populated prefix returns deepest match`() {
        val cache = PrefixCache(fromSha = "BASE")
        cache.put(listOf(0), "shaA")
        cache.put(listOf(0, 1), "shaB")

        val (prefix1, sha1) = cache.deepestHit(listOf(0, 1, 2))
        assertEquals(listOf(0, 1), prefix1)
        assertEquals("shaB", sha1)

        // Diverges after the first element — only [0] is cached on this path.
        val (prefix2, sha2) = cache.deepestHit(listOf(0, 2, 1))
        assertEquals(listOf(0), prefix2)
        assertEquals("shaA", sha2)
    }

    @Test
    fun `divergent orderings share no prefix`() {
        val cache = PrefixCache(fromSha = "BASE")
        cache.put(listOf(0), "shaA")
        cache.put(listOf(0, 1), "shaB")

        val (prefix, sha) = cache.deepestHit(listOf(1, 0, 2))
        assertEquals(emptyList(), prefix)
        assertEquals("BASE", sha)
    }

    @Test
    fun `non-prefix superset of cached entry is not a hit`() {
        // [0,1] is cached but [1] alone is NOT — even though {0,1}
        // ⊇ {1}, the ordered cache should only hit on prefix
        // equality, never on subset.
        val cache = PrefixCache(fromSha = "BASE")
        cache.put(listOf(0, 1), "shaB")

        val (prefix, sha) = cache.deepestHit(listOf(1, 0))
        assertEquals(emptyList(), prefix)
        assertEquals("BASE", sha)
    }

    @Test
    fun `put each prefix as we walk then deepestHit returns the deepest`() {
        // Synthesis populates prefixes contiguously as it commits each
        // suffix step (length 1, 2, 3, …). `deepestHit` walks
        // left-to-right and breaks on the first cache miss — so the
        // intermediate prefixes must be present for a deeper match.
        val cache = PrefixCache(fromSha = "BASE")
        cache.put(listOf(3), "shaA")
        cache.put(listOf(3, 1), "shaB")
        cache.put(listOf(3, 1, 2), "shaC")

        val (prefix, sha) = cache.deepestHit(listOf(3, 1, 2))
        assertEquals(listOf(3, 1, 2), prefix)
        assertEquals("shaC", sha)
    }

    @Test
    fun `gap in cached prefix chain breaks at the gap`() {
        // The 3-element prefix is cached but its predecessors aren't.
        // `deepestHit` must NOT skip past the gap — it returns the
        // empty prefix because length-1 misses.
        val cache = PrefixCache(fromSha = "BASE")
        cache.put(listOf(3, 1, 2), "shaC")

        val (prefix, sha) = cache.deepestHit(listOf(3, 1, 2))
        assertEquals(emptyList(), prefix)
        assertEquals("BASE", sha)
    }
}
