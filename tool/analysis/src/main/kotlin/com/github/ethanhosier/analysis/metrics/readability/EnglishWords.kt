package com.github.ethanhosier.analysis.metrics.readability

internal object EnglishWords {

    private val words: Set<String> by lazy { load() }

    fun contains(word: String): Boolean {
        if (word.length < 2) return false
        return words.contains(word.lowercase())
    }

    private fun load(): Set<String> {
        val stream = EnglishWords::class.java.classLoader
            .getResourceAsStream("english-words.txt")
            ?: error("missing classpath resource: english-words.txt")
        return stream.bufferedReader().useLines { lines ->
            lines.map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toHashSet()
        }
    }
}
