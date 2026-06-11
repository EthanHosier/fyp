package com.github.ethanhosier.analysis.alternative.synthesise

internal class PrefixTrie {

    class Node(
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
