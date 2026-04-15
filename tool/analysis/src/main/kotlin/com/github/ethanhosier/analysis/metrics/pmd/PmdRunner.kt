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
                PmdViolation(
                    file = relativize(v.fileId.absolutePath, rootStr),
                    rule = v.rule.name,
                    ruleSet = v.rule.ruleSetName ?: "",
                    priority = v.rule.priority.priority,
                    beginLine = v.beginLine,
                    endLine = v.endLine,
                    message = v.description,
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
            )
        }
    }

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
    }
}
