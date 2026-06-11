package com.github.ethanhosier.analysis.metrics.cpd

import com.github.ethanhosier.analysis.metrics.util.SnippetIdentity
import com.github.ethanhosier.analysis.metrics.util.SourceSnippet
import net.sourceforge.pmd.cpd.CPDConfiguration
import net.sourceforge.pmd.cpd.CpdAnalysis
import net.sourceforge.pmd.lang.java.JavaLanguageModule
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

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
                    val identity = occurrences
                        .firstNotNullOfOrNull { SnippetIdentity.fromPatch(it.snippet?.patch) }
                        .orEmpty()
                    duplications += CpdDuplication(
                        tokens = tokens,
                        lines = lines,
                        occurrences = occurrences,
                        identity = identity,
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
