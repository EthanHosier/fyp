package com.github.ethanhosier.analysis.metrics.cpd

import com.github.ethanhosier.analysis.metrics.util.SourceSnippet
import net.sourceforge.pmd.cpd.CPDConfiguration
import net.sourceforge.pmd.cpd.CpdAnalysis
import net.sourceforge.pmd.lang.java.JavaLanguageModule
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

/**
 * Runs PMD CPD across a project root and returns a typed [CpdResult].
 *
 * Uses the PMD 7.x programmatic CPD API — sibling of the rule engine used by
 * [com.github.ethanhosier.analysis.metrics.pmd.PmdRunner]. The two engines
 * share the same jar but have independent configs and lifecycles; keeping the
 * runner separate matches how every other metric (CK / build / tests) is
 * wired.
 */
/**
 * Defaults:
 *  - `minimumTokens = 50` (CPD's Java default is 75). We track small refactorings
 *    where the duplicated unit is often a single 8-10 line method — 75 tokens
 *    misses those entirely.
 *  - `ignoreIdentifiers = true` — Type-2 clone detection. Identifier names
 *    (method/variable) are normalised, so `computeTotal` and `computeTotal2`
 *    with identical bodies match. This is exactly what Extract Method removes,
 *    so it's the right sensitivity for tracking refactoring behaviour.
 */
class CpdRunner(
    private val minimumTokens: Int = 50,
    private val ignoreIdentifiers: Boolean = true,
    private val javaVersion: String = "21",
) {

    fun run(projectDir: Path): CpdResult {
        require(Files.isDirectory(projectDir)) { "not a directory: $projectDir" }
        val root = projectDir.toAbsolutePath().normalize()
        val rootStr = root.toString()
        val start = System.currentTimeMillis()

        val languageVersion = JavaLanguageModule.getInstance().getVersion(javaVersion)
            ?: error("unknown java language version: $javaVersion")
        val config = CPDConfiguration().apply {
            setDefaultLanguageVersion(languageVersion)
            setOnlyRecognizeLanguage(languageVersion.language)
            addInputPath(root)
            minimumTileSize = minimumTokens
            isSkipDuplicates = true
            isIgnoreIdentifiers = ignoreIdentifiers
        }

        val duplications = mutableListOf<CpdDuplication>()
        val processingErrors = mutableListOf<CpdProcessingError>()
        var totalTokens = 0

        CpdAnalysis.create(config).use { cpd ->
            cpd.performAnalysis { report ->
                totalTokens = report.numberOfTokensPerFile.values.sum()
                for (match in report.matches) {
                    val lines = match.lineCount
                    val tokens = match.tokenCount
                    val occurrences = match.markSet.map { mark ->
                        val loc = mark.location
                        val relFile = relativize(loc.fileId.absolutePath, rootStr)
                        CpdOccurrence(
                            file = relFile,
                            beginLine = loc.startLine,
                            endLine = loc.endLine,
                            snippet = SourceSnippet
                                .load(root, relFile, loc.startLine, loc.endLine)
                                ?.let(::CpdSnippet),
                        )
                    }
                    duplications += CpdDuplication(
                        tokens = tokens,
                        lines = lines,
                        occurrences = occurrences,
                    )
                }
                for (err in report.processingErrors) {
                    processingErrors += CpdProcessingError(
                        file = relativize(err.fileId.absolutePath, rootStr),
                        message = err.msg ?: err.error?.message ?: err::class.java.name,
                    )
                }
            }
        }

        val duplicatedLines = duplications.sumOf { it.lines * it.occurrences.size }
        val duplicatedTokens = duplications.sumOf { it.tokens * it.occurrences.size }
        val largestBlockLines = duplications.maxOfOrNull { it.lines } ?: 0
        val largestBlockTokens = duplications.maxOfOrNull { it.tokens } ?: 0
        val filesInvolved = duplications.flatMap { dup -> dup.occurrences.map { it.file } }.toSet().size
        val totalLines = countJavaLines(root)
        val share = if (totalLines == 0) 0.0 else duplicatedLines.toDouble() / totalLines

        return CpdResult(
            duplicationBlocks = duplications.size,
            duplicatedLines = duplicatedLines,
            duplicatedTokens = duplicatedTokens,
            totalLines = totalLines,
            totalTokens = totalTokens,
            duplicatedLinesShare = share,
            largestBlockLines = largestBlockLines,
            largestBlockTokens = largestBlockTokens,
            filesInvolvedInDuplication = filesInvolved,
            duplications = duplications,
            processingErrors = processingErrors,
            durationMs = System.currentTimeMillis() - start,
        )
    }

    /**
     * PMD's CPDReport does not expose total-lines across all scanned files, so
     * we count them ourselves. Walks [root] the same way CPD does — recursive,
     * `*.java` — and uses the raw line count of each file.
     */
    private fun countJavaLines(root: Path): Int {
        var total = 0
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.extension == "java" }
                .forEach { path ->
                    Files.lines(path).use { lines -> total += lines.count().toInt() }
                }
        }
        return total
    }

    private fun relativize(absPath: String, rootStr: String): String {
        if (absPath.startsWith(rootStr)) {
            return absPath.removePrefix(rootStr).trimStart('/', '\\')
        }
        return absPath
    }
}
