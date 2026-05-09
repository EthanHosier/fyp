package com.github.ethanhosier.analysis.alternative.synthesise

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PrefixTrieTest {

    @Test
    fun `single ordering produces a chain of nodes`() {
        val trie = PrefixTrie.of(listOf(listOf(0, 1, 2)))
        val n0 = trie.root.children[0]; assertNotNull(n0)
        val n01 = n0.children[1]; assertNotNull(n01)
        val n012 = n01.children[2]; assertNotNull(n012)
        assertEquals(0, n012.children.size, "leaf has no children")
        assertEquals(3, trie.size())
    }

    @Test
    fun `orderings sharing a prefix share trie nodes`() {
        val trie = PrefixTrie.of(listOf(listOf(0, 1, 2), listOf(0, 1, 3)))
        val n0 = trie.root.children[0]!!
        val n01 = n0.children[1]!!
        assertEquals(2, n01.children.size)
        assertNotNull(n01.children[2])
        assertNotNull(n01.children[3])
        // {0}, {0,1}, {0,1,2}, {0,1,3} = 4 distinct prefixes.
        assertEquals(4, trie.size())
    }

    @Test
    fun `orderings with no shared prefix produce parallel branches`() {
        val trie = PrefixTrie.of(listOf(listOf(0, 1), listOf(1, 0)))
        assertEquals(2, trie.root.children.size)
        // {0}, {0,1}, {1}, {1,0} = 4 distinct prefixes.
        assertEquals(4, trie.size())
    }

    @Test
    fun `empty input produces an empty trie`() {
        val trie = PrefixTrie.of(emptyList())
        assertEquals(0, trie.root.children.size)
        assertEquals(0, trie.size())
    }

    @Test
    fun `child insertion order is preserved across sibling enumeration`() {
        val trie = PrefixTrie.of(listOf(listOf(2), listOf(0), listOf(1)))
        val keys = trie.root.children.keys.toList()
        assertEquals(listOf(2, 0, 1), keys)
    }

    @Test
    fun `unrelated prefixes do not collide in lookup`() {
        val trie = PrefixTrie.of(listOf(listOf(0, 1), listOf(0, 2)))
        val n0 = trie.root.children[0]!!
        assertNotNull(n0.children[1])
        assertNotNull(n0.children[2])
        assertNull(n0.children[1]!!.children[2])
    }
}
