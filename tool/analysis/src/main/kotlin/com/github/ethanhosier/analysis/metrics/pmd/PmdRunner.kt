package com.github.ethanhosier.analysis.metrics.pmd

import net.sourceforge.pmd.PMDConfiguration
import net.sourceforge.pmd.PmdAnalysis
import net.sourceforge.pmd.lang.java.JavaLanguageModule
import net.sourceforge.pmd.lang.rule.RuleSet
import java.nio.file.Files
import java.nio.file.Path

/**
 * Runs PMD across a project root and returns a typed [PmdResult].
 *
 * Uses the PMD 7.x programmatic API: build a [PMDConfiguration], hand it to
 * [PmdAnalysis.create], and collect the [net.sourceforge.pmd.reporting.Report]
 * from `performAnalysisAndCollectReport()`.
 */
class PmdRunner(
    private val ruleSets: List<String> = DEFAULT_RULESETS,
    private val javaVersion: String = "21",
) {

    /**
     * @param projectDir root directory to scan recursively for `.java` files
     */
    fun run(projectDir: Path): PmdResult {
        require(Files.isDirectory(projectDir)) { "not a directory: $projectDir" }
        val root = projectDir.toAbsolutePath().normalize()
        val rootStr = root.toString()
        val start = System.currentTimeMillis()

        val languageVersion = JavaLanguageModule.getInstance().getVersion(javaVersion)
            ?: error("unknown java language version: $javaVersion")
        val config = PMDConfiguration().apply {
            setDefaultLanguageVersion(languageVersion)
            addInputPath(root)
            ruleSets.forEach { addRuleSet(it) }
        }

        val collector = PmdMetricsCollectorRule()

        return PmdAnalysis.create(config).use { pmd ->
            pmd.addRuleSet(RuleSet.forSingleRule(collector))
            val report = pmd.performAnalysisAndCollectReport()

            val violations = report.violations.map { v ->
                val file = relativize(v.fileId.absolutePath, rootStr)
                PmdViolation(
                    file = file,
                    rule = v.rule.name,
                    ruleSet = v.rule.ruleSetName ?: "",
                    priority = v.rule.priority.priority,
                    beginLine = v.beginLine,
                    endLine = v.endLine,
                    message = v.description,
                    snippet = loadSnippet(root, file, v.beginLine, v.endLine),
                )
            }
            val errors = report.processingErrors.map { e ->
                PmdProcessingError(
                    file = relativize(e.fileId.absolutePath, rootStr),
                    message = e.msg ?: e.error?.message ?: e::class.java.name,
                )
            }

            val classMetrics = collector.classMetrics.map {
                PmdClassMetrics(
                    className = it.className,
                    file = relativize(it.file, rootStr),
                    ncss = it.ncss,
                    atfd = it.atfd,
                    noam = it.noam,
                    nopa = it.nopa,
                    woc = it.woc,
                )
            }
            val methodMetrics = collector.methodMetrics.map {
                PmdMethodMetrics(
                    className = it.className,
                    signature = it.signature,
                    file = relativize(it.file, rootStr),
                    cyclo = it.cyclo,
                    cognitive = it.cognitive,
                    npath = it.npath,
                    ncss = it.ncss,
                    atfd = it.atfd,
                )
            }

            PmdResult(
                violations = violations,
                classMetrics = classMetrics,
                methodMetrics = methodMetrics,
                processingErrors = errors,
                durationMs = System.currentTimeMillis() - start,
            )
        }
    }

    /**
     * Reads `[beginLine - SNIPPET_CONTEXT_LINES .. endLine + SNIPPET_CONTEXT_LINES]`
     * from the worktree file (clamped to file bounds) and wraps it as a
     * self-contained mini unified-diff string: one file header, one hunk
     * with every body line marked as context (` ` prefix), absolute line
     * numbers in the `@@` header. This is *not* a real diff — the format
     * is reused so the dashboard's `@pierre/diffs` renderer can display
     * the snippet with Shiki syntax highlighting and a correctly numbered
     * gutter. Context width matches [com.github.ethanhosier.analysis.reconstruct.GitRunner.diffPatch]'s
     * default so smell snippets and diff hunks align visually.
     *
     * Returns null when the file is missing/unreadable or the requested
     * range is outside the file — same posture as PMD processing errors.
     */
    private fun loadSnippet(
        root: Path,
        relPath: String,
        beginLine: Int,
        endLine: Int,
    ): PmdViolationSnippet? = runCatching {
        val lines = Files.readAllLines(root.resolve(relPath), Charsets.UTF_8)
        val from = (beginLine - SNIPPET_CONTEXT_LINES - 1).coerceAtLeast(0)
        val to = (endLine + SNIPPET_CONTEXT_LINES).coerceAtMost(lines.size)
        if (from >= to) return@runCatching null
        val startLine = from + 1
        val count = to - from
        val body = lines.subList(from, to).joinToString("\n") { " $it" }
        // Trailing newline matches `git diff` output and keeps the
        // dashboard's parser from warning about an unterminated last line.
        val patch = buildString {
            append("diff --git a/").append(relPath).append(" b/").append(relPath).append('\n')
            append("--- a/").append(relPath).append('\n')
            append("+++ b/").append(relPath).append('\n')
            append("@@ -").append(startLine).append(',').append(count)
                .append(" +").append(startLine).append(',').append(count).append(" @@\n")
            append(body).append('\n')
        }
        PmdViolationSnippet(patch = patch)
    }.getOrNull()

    private fun relativize(absPath: String, rootStr: String): String {
        if (absPath.startsWith(rootStr)) {
            return absPath.removePrefix(rootStr).trimStart('/', '\\')
        }
        return absPath
    }

    companion object {
        /**
         * Decent signal-to-noise default: real correctness + design issues,
         * no stylistic/documentation nits. Override via constructor if a
         * downstream consumer wants a different scope.
         */
        val DEFAULT_RULESETS: List<String> = listOf(
            "category/java/bestpractices.xml",
            "category/java/errorprone.xml",
            "category/java/design.xml",
        )

        /** Lines of surrounding source kept either side of a violation range. */
        private const val SNIPPET_CONTEXT_LINES = 3
    }
}
