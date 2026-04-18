package com.github.ethanhosier.analysis.metrics.readability

/**
 * Lazily-loaded English wordlist used by [ReadabilityRunner] to compute
 * `dictionaryWordRatio`. Bundled as a classpath resource at
 * `english-words.txt` — one word per line, lower-case.
 *
 * Source: dwyl/english-words (`words_alpha.txt`, public domain). ~370k
 * entries; intentionally broad so we catch domain-specific but real words
 * (`http`, `util`, `json`). Splits shorter than 2 chars should not be
 * checked — they add noise and are handled separately via single-letter
 * ratio.
 */
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
