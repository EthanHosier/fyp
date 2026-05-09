package com.github.ethanhosier.analysis.alternative.synthesise

/**
 * Trie of ordered prefixes built from a set of orderings. Each node
 * represents one unique prefix; the root is the empty prefix.
 *
 * Used by [ReorderSynthesiser]'s DFS: each non-root node = one
 * materialised commit, each forward edge = one refactoring apply,
 * each back edge = one `git checkout --detach <parent>` +
 * `refreshProject`. Visiting every node once visits every distinct
 * prefix across all orderings exactly once, with no redundant
 * applies.
 *
 * Children are kept in a `LinkedHashMap` so DFS traversal order is
 * deterministic and follows the order each child was first inserted
 * — orderings that share a long prefix end up grouped adjacently in
 * traversal order, which keeps the synthesis log readable.
 */
internal class PrefixTrie {

    class Node(
        /** The spec index applied at the edge from this node's parent.
         *  `null` only at the root. */
        val specIdx: Int?,
    ) {
        val children: MutableMap<Int, Node> = LinkedHashMap()
    }

    val root: Node = Node(specIdx = null)

    fun insert(ordering: List<Int>) {
        var cur = root
        for (idx in ordering) {
            cur = cur.children.getOrPut(idx) { Node(specIdx = idx) }
        }
    }

    /** Total node count excluding the root — i.e. the number of
     *  distinct non-empty prefixes across all inserted orderings.
     *  Equals the apply count of a clean DFS. */
    fun size(): Int {
        var n = 0
        fun visit(node: Node) {
            for (child in node.children.values) {
                n++
                visit(child)
            }
        }
        visit(root)
        return n
    }

    companion object {
        fun of(orderings: Iterable<List<Int>>): PrefixTrie {
            val trie = PrefixTrie()
            for (o in orderings) trie.insert(o)
            return trie
        }
    }
}
