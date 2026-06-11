package com.github.ethanhosier.analysis.cli

import com.github.ethanhosier.analysis.pipeline.AnalysisReport
import com.github.ethanhosier.analysis.pipeline.AnalysisPipeline
import com.github.ethanhosier.analysis.refactoring.RefactoringClientFactory
import com.github.ethanhosier.ideplugin.model.TraceEvent
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess


private val reportJson = Json { prettyPrint = true; encodeDefaults = true }
private val eventJson = Json { encodeDefaults = true }

fun main(args: Array<String>) {
    val opts = parseArgs(args) ?: run {
        System.err.println("usage: analysis <session-folder> [--parallelism=N]")
        exitProcess(2)
    }

    val totalStart = System.currentTimeMillis()
    val dataArea = Files.createTempDirectory("analysis-equinox-").resolve("osgi")
    val refactoringClient = RefactoringClientFactory.create(dataArea)
    val result = refactoringClient.use { refactoringClient ->
        AnalysisPipeline(refactoringClient = refactoringClient, parallelism = opts.parallelism)
            .run(opts.sessionFolder)
    }
    val totalDurationMs = System.currentTimeMillis() - totalStart

    val reportPath = opts.sessionFolder.resolve("analysis-report.json")
    Files.writeString(reportPath, reportJson.encodeToString(AnalysisReport.serializer(), result.report))

    val normalizedPath = opts.sessionFolder.resolve("normalized-events.jsonl")
    Files.writeString(
        normalizedPath,
        result.trace.events.joinToString(separator = "\n", postfix = "\n") { event ->
            eventJson.encodeToString(TraceEvent.serializer(), event)
        },
    )

    val meta = result.trace.metadata
    val uniqueShas = result.metricsSummary.totalShas
    val eventCount = result.trace.events.size
    println("session:    ${meta.sessionId}")
    println("name:       ${meta.name}")
    println("project:    ${meta.projectName} (${meta.projectPath})")
    println("started:    ${meta.startTime}")
    println("ended:      ${meta.endTime}")
    println("events:     $eventCount")
    println("repo:       ${result.reconstruction.repoDir}")
    println("commits:    $uniqueShas unique (${eventCount - uniqueShas} events collapsed to prior SHA)")
    println(
        "metrics:    $uniqueShas SHAs processed (${result.metricsSummary.computed} computed), " +
            "${result.metricsSummary.buildOk} build-ok, ${result.metricsSummary.testsOk} tests-ok " +
            "in ${result.metricsDurationMs / 1000}s (parallelism=${opts.parallelism})",
    )
    val altSummary = result.alternativeSummary
    println(
        "alt-traj:   ${altSummary.synthesised.size}/${altSummary.candidates} synthesised " +
            "(${altSummary.skipped.size} skipped) in ${result.alternativeDurationMs / 1000}s",
    )
    val steps = result.minerSummary.steps
    val ideRelevantCount = steps.count { it.refactoring.ideRelevant }
    val distinctWindows = steps.map { it.fromSha to it.toSha }.toSet().size
    println(
        "miner:      ${result.minerSummary.checkpointsAnalysed} checkpoint(s) analysed, " +
            "${steps.size} refactoring step(s) across $distinctWindows window(s) " +
            "($ideRelevantCount ide-relevant) in ${result.minerDurationMs / 1000}s",
    )
    println("report:     ${reportPath.toAbsolutePath()}")
    println("normalized: ${normalizedPath.toAbsolutePath()}")
    println("total:      ${totalDurationMs / 1000}s")
}

private data class CliOptions(val sessionFolder: Path, val parallelism: Int)

private fun parseArgs(args: Array<String>): CliOptions? {
    var sessionFolder: Path? = null
    var parallelism: Int = AnalysisPipeline.defaultParallelism()

    for (arg in args) {
        when {
            arg.startsWith("--parallelism=") -> {
                parallelism = arg.substringAfter("=").toIntOrNull()?.takeIf { it > 0 }
                    ?: return null
            }
            arg.startsWith("--") -> return null
            else -> {
                if (sessionFolder != null) return null
                sessionFolder = Path.of(arg)
            }
        }
    }
    val folder = sessionFolder ?: return null
    return CliOptions(folder, parallelism)
}
