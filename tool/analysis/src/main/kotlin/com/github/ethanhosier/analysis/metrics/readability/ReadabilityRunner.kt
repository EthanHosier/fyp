package com.github.ethanhosier.analysis.metrics.readability

import net.sourceforge.pmd.PMDConfiguration
import net.sourceforge.pmd.PmdAnalysis
import net.sourceforge.pmd.lang.java.JavaLanguageModule
import net.sourceforge.pmd.lang.rule.RuleSet
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

/**
 * Computes [ReadabilityResult] for a checkpoint. Two passes:
 *
 *  1. PMD AST pass (shared with [com.github.ethanhosier.analysis.metrics.pmd.PmdRunner]'s
 *     infrastructure but independent analysis) collects the declared
 *     identifiers in every class and method.
 *  2. A raw text scan of each `.java` file computes line-length,
 *     blank/comment/code ratios, and indentation depth — cheap, tokenizer
 *     would be overkill.
 *
 * Per-file identifier stats are the union of every identifier declared in
 * classes and methods within that file.
 */
class ReadabilityRunner(
    private val javaVersion: String = "21",
    private val tabWidth: Int = 4,
) {

    fun run(projectDir: Path): ReadabilityResult {
        require(Files.isDirectory(projectDir)) { "not a directory: $projectDir" }
        val root = projectDir.toAbsolutePath().normalize()
        val rootStr = root.toString()
        val start = System.currentTimeMillis()

        val collector = collectIdentifiers(root)

        val classByFile = collector.classes.groupBy { it.file }
        val methodByFile = collector.methods.groupBy { it.file }

        val perClass = collector.classes.map {
            ClassReadability(
                className = it.className,
                file = relativize(it.file, rootStr),
                loc = (it.endLine - it.beginLine + 1).coerceAtLeast(0),
                identifiers = identifierStatsFor(it.identifiers),
            )
        }
        val perMethod = collector.methods.map {
            MethodReadability(
                className = it.className,
                signature = it.signature,
                file = relativize(it.file, rootStr),
                loc = (it.endLine - it.beginLine + 1).coerceAtLeast(0),
                identifiers = identifierStatsFor(it.identifiers),
            )
        }

        val perFile = mutableListOf<FileReadability>()
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.extension == "java" }
                .forEach { path ->
                    val abs = path.toAbsolutePath().normalize().toString()
                    val ids = (classByFile[abs].orEmpty().flatMap { it.identifiers } +
                        methodByFile[abs].orEmpty().flatMap { it.identifiers })
                    perFile += analyseFile(path, relativize(abs, rootStr), ids)
                }
        }
        perFile.sortBy { it.file }

        return ReadabilityResult(
            perFile = perFile,
            perClass = perClass,
            perMethod = perMethod,
            summary = summarise(perFile, perClass, perMethod),
            durationMs = System.currentTimeMillis() - start,
        )
    }

    private fun summarise(
        files: List<FileReadability>,
        classes: List<ClassReadability>,
        methods: List<MethodReadability>,
    ): ReadabilitySummary {
        if (files.isEmpty()) return ReadabilitySummary.EMPTY
        val fileCount = files.size
        val classCount = classes.size
        val methodCount = methods.size

        val avgLineLength = files.map { it.avgLineLength }.average()
        val maxLineLength = files.maxOf { it.maxLineLength }
        val avgCommentRatio = files.map { it.commentLineRatio }.average()
        val avgBlankRatio = files.map { it.blankLineRatio }.average()
        val avgIndentation = files.map { it.avgIndentation }.average()
        val maxIndentation = files.maxOf { it.maxIndentation }

        // Identifier aggregates: weighted by per-scope count so a file with many
        // identifiers dominates appropriately — equivalent to pooling raw names.
        val totalIds = (classes.sumOf { it.identifiers.count } + methods.sumOf { it.identifiers.count })
            .coerceAtLeast(1)
        fun pooled(select: (IdentifierStats) -> Double, count: (IdentifierStats) -> Int): Double {
            val sum = classes.sumOf { select(it.identifiers) * count(it.identifiers) } +
                methods.sumOf { select(it.identifiers) * count(it.identifiers) }
            return sum / totalIds
        }
        val avgIdentifierLength = pooled({ it.avgLength }, { it.count })
        val singleLetterRatio = pooled({ it.singleLetterRatio }, { it.count })
        val avgWordCount = pooled({ it.avgWordCount }, { it.count })
        val dictionaryWordRatio = pooled({ it.dictionaryWordRatio }, { it.count })

        return ReadabilitySummary(
            fileCount = fileCount,
            classCount = classCount,
            methodCount = methodCount,
            avgLineLength = avgLineLength,
            maxLineLength = maxLineLength,
            avgCommentRatio = avgCommentRatio,
            avgBlankRatio = avgBlankRatio,
            avgIndentation = avgIndentation,
            maxIndentation = maxIndentation,
            avgIdentifierLength = avgIdentifierLength,
            singleLetterRatio = singleLetterRatio,
            avgWordCount = avgWordCount,
            dictionaryWordRatio = dictionaryWordRatio,
            worstClassLoc = classes.maxOfOrNull { it.loc } ?: 0,
            worstMethodLoc = methods.maxOfOrNull { it.loc } ?: 0,
        )
    }

    private fun collectIdentifiers(root: Path): ReadabilityCollectorRule {
        val languageVersion = JavaLanguageModule.getInstance().getVersion(javaVersion)
            ?: error("unknown java language version: $javaVersion")
        val config = PMDConfiguration().apply {
            setDefaultLanguageVersion(languageVersion)
            addInputPath(root)
        }
        val collector = ReadabilityCollectorRule()
        PmdAnalysis.create(config).use { pmd ->
            pmd.addRuleSet(RuleSet.forSingleRule(collector))
            pmd.performAnalysisAndCollectReport()
        }
        return collector
    }

    private fun analyseFile(path: Path, relPath: String, identifiers: List<String>): FileReadability {
        val lines = Files.readAllLines(path)
        val totalLines = lines.size

        var codeLines = 0
        var commentLines = 0
        var blankLines = 0
        var nonBlankLengthSum = 0L
        var nonBlankCount = 0
        var maxLineLength = 0
        var indentationSum = 0L
        var indentationCount = 0
        var maxIndentation = 0
        var inBlockComment = false

        for (raw in lines) {
            val line = raw
            val length = line.length
            if (length > maxLineLength) maxLineLength = length

            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                blankLines++
                continue
            }

            nonBlankCount++
            nonBlankLengthSum += length

            val indent = indentationOf(line, tabWidth)
            indentationSum += indent
            indentationCount++
            if (indent > maxIndentation) maxIndentation = indent

            val classification = classifyLine(trimmed, inBlockComment)
            when (classification.kind) {
                LineKind.CODE -> codeLines++
                LineKind.COMMENT -> commentLines++
                LineKind.MIXED -> {
                    codeLines++
                    commentLines++
                }
            }
            inBlockComment = classification.endsInsideBlockComment
        }

        val nonBlank = nonBlankCount.coerceAtLeast(1)
        return FileReadability(
            file = relPath,
            totalLines = totalLines,
            codeLines = codeLines,
            commentLines = commentLines,
            blankLines = blankLines,
            commentLineRatio = if (totalLines == 0) 0.0 else commentLines.toDouble() / totalLines,
            blankLineRatio = if (totalLines == 0) 0.0 else blankLines.toDouble() / totalLines,
            avgLineLength = if (nonBlankCount == 0) 0.0 else nonBlankLengthSum.toDouble() / nonBlank,
            maxLineLength = maxLineLength,
            avgIndentation = if (indentationCount == 0) 0.0 else indentationSum.toDouble() / indentationCount,
            maxIndentation = maxIndentation,
            identifiers = identifierStatsFor(identifiers),
        )
    }

    private fun indentationOf(line: String, tabWidth: Int): Int {
        var count = 0
        for (c in line) {
            when (c) {
                ' ' -> count++
                '\t' -> count += tabWidth
                else -> return count
            }
        }
        return count
    }

    private enum class LineKind { CODE, COMMENT, MIXED }
    private data class LineClass(val kind: LineKind, val endsInsideBlockComment: Boolean)

    /**
     * Classify a non-blank trimmed source line. Handles single-line `//`,
     * block `/…/` forms, and multi-line block comments by carrying
     * [inBlockComment] between calls.
     *
     *  - comment-only lines → COMMENT
     *  - code-only lines    → CODE
     *  - code plus a trailing line comment or a block comment share → MIXED
     */
    private fun classifyLine(trimmed: String, inBlockComment: Boolean): LineClass {
        var insideBlock = inBlockComment
        var sawCode = false
        var sawComment = false
        var i = 0
        while (i < trimmed.length) {
            if (insideBlock) {
                val close = trimmed.indexOf("*/", i)
                sawComment = true
                if (close < 0) return LineClass(if (sawCode) LineKind.MIXED else LineKind.COMMENT, true)
                i = close + 2
                insideBlock = false
                continue
            }
            // Skip whitespace between tokens cheaply.
            while (i < trimmed.length && trimmed[i].isWhitespace()) i++
            if (i >= trimmed.length) break
            if (i + 1 < trimmed.length && trimmed[i] == '/' && trimmed[i + 1] == '/') {
                sawComment = true
                break
            }
            if (i + 1 < trimmed.length && trimmed[i] == '/' && trimmed[i + 1] == '*') {
                insideBlock = true
                i += 2
                continue
            }
            sawCode = true
            i++
        }
        val kind = when {
            sawCode && sawComment -> LineKind.MIXED
            sawComment -> LineKind.COMMENT
            else -> LineKind.CODE
        }
        return LineClass(kind, insideBlock)
    }

    private fun relativize(absPath: String, rootStr: String): String {
        if (absPath.startsWith(rootStr)) {
            return absPath.removePrefix(rootStr).trimStart('/', '\\')
        }
        return absPath
    }
}
