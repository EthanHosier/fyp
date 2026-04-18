package com.github.ethanhosier.analysis.metrics.readability

/**
 * Splits an identifier into its camelCase / snake_case / digit parts.
 *
 *  - `computeTotal`  → ["compute", "total"]
 *  - `HTTP_PORT`     → ["HTTP", "PORT"]
 *  - `parseXMLFile`  → ["parse", "XML", "File"]
 *  - `x1y2`          → ["x", "1", "y", "2"]
 *  - `_foo$bar`      → ["foo", "bar"]
 *
 * Used for both the average-word-count metric and the dictionary lookup.
 */
internal fun splitIdentifier(name: String): List<String> {
    if (name.isEmpty()) return emptyList()
    val parts = mutableListOf<String>()
    val current = StringBuilder()

    fun flush() {
        if (current.isNotEmpty()) {
            parts += current.toString()
            current.clear()
        }
    }

    var prevKind: CharKind? = null
    for ((i, c) in name.withIndex()) {
        val kind = kindOf(c) ?: run {
            // separator (_, $, digit-to-non-digit handled below)
            flush()
            prevKind = null
            return@run null
        }
        val next = name.getOrNull(i + 1)
        val boundary = when {
            prevKind == null -> false
            prevKind != kind -> {
                // Transition. Lower → Upper: new part at the upper (parseXML → ["parse", "XML"]).
                // Digit ↔ letter: always a boundary.
                // Upper → lower: carries over (so XMLFile splits correctly via lookahead below).
                prevKind == CharKind.LOWER && kind == CharKind.UPPER ||
                    prevKind == CharKind.DIGIT || kind == CharKind.DIGIT
            }
            // Run of uppers, then we're about to hit a lower: back-split.
            // HTTPPort -> ["HTTP", "Port"]. Detected when current UPPER is followed by LOWER.
            kind == CharKind.UPPER && next != null && kindOf(next) == CharKind.LOWER && current.length >= 2 -> true
            else -> false
        }
        if (boundary) flush()
        current.append(c)
        prevKind = kind
    }
    flush()
    return parts
}

private enum class CharKind { LOWER, UPPER, DIGIT }

private fun kindOf(c: Char): CharKind? = when {
    c.isLowerCase() -> CharKind.LOWER
    c.isUpperCase() -> CharKind.UPPER
    c.isDigit() -> CharKind.DIGIT
    else -> null
}

/**
 * Reduce a list of declared identifier names to aggregate [IdentifierStats].
 */
internal fun identifierStatsFor(names: List<String>): IdentifierStats {
    if (names.isEmpty()) return IdentifierStats.EMPTY
    val count = names.size
    val totalLength = names.sumOf { it.length }
    val singleLetter = names.count { it.length == 1 }

    var splitPartsTotal = 0
    var dictHits = 0
    var dictEligible = 0
    for (name in names) {
        val parts = splitIdentifier(name)
        splitPartsTotal += parts.size
        for (part in parts) {
            if (part.length < 2) continue
            if (!part.all { it.isLetter() }) continue
            dictEligible++
            if (EnglishWords.contains(part)) dictHits++
        }
    }
    return IdentifierStats(
        count = count,
        avgLength = totalLength.toDouble() / count,
        singleLetterRatio = singleLetter.toDouble() / count,
        avgWordCount = splitPartsTotal.toDouble() / count,
        dictionaryWordRatio = if (dictEligible == 0) 0.0 else dictHits.toDouble() / dictEligible,
    )
}
