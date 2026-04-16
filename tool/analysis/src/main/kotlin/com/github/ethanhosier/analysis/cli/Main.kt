package com.github.ethanhosier.analysis.cli

import com.github.ethanhosier.analysis.ingest.TraceLoader
import com.github.ethanhosier.analysis.metrics.MetricsRunner
import com.github.ethanhosier.analysis.model.Trace
import com.github.ethanhosier.analysis.normalize.TraceNormalizer
import com.github.ethanhosier.analysis.reconstruct.ShadowRepoBuilder
import com.github.ethanhosier.ideplugin.model.TraceEvent
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Temporary harness. Runs the full analysis pipeline (load → normalize →
 * reconstruct → metrics) over one session folder. Will be replaced by a
 * proper Clikt command once the stages stabilise.
 */

/*
 ./gradlew :analysis:run --args="/path/to/session [--parallelism=N]" -q
 */

fun main(args: Array<String>) {
    val opts = parseArgs(args) ?: run {
        System.err.println("usage: analysis <session-folder> [--parallelism=N]")
        exitProcess(2)
    }

    val raw = TraceLoader().load(opts.sessionFolder)
    val trace = TraceNormalizer.normalize(raw)

    val reordered = raw.events.indices.count { i -> raw.events[i].id != trace.events[i].id }
    val scratchPath = saveNormalizedTraceToJson(trace)

    val reconstruction = ShadowRepoBuilder().build(opts.sessionFolder, trace)
    val uniqueShas = reconstruction.eventCommits.mapping.values.toSet().size

    println("session:    ${trace.metadata.sessionId}")
    println("name:       ${trace.metadata.name}")
    println("project:    ${trace.metadata.projectName} (${trace.metadata.projectPath})")
    println("started:    ${trace.metadata.startTime}")
    println("ended:      ${trace.metadata.endTime}")
    println("events:     ${trace.events.size}")
    println("reordered:  $reordered event(s) moved by normalize")
    println("wrote:      ${scratchPath.toAbsolutePath()}")
    println("repo:       ${reconstruction.repoDir}")
    println("commits:    $uniqueShas unique (${trace.events.size - uniqueShas} events collapsed to prior SHA)")

    val metricsStart = System.currentTimeMillis()
    val metrics = MetricsRunner(parallelism = opts.parallelism).run(reconstruction, opts.sessionFolder)
    val metricsDurationMs = System.currentTimeMillis() - metricsStart

    println(
        "metrics:    ${metrics.totalShas} SHAs processed " +
            "(${metrics.computed} computed, ${metrics.reused} reused), " +
            "${metrics.buildOk} build-ok, ${metrics.testsOk} tests-ok " +
            "in ${metricsDurationMs / 1000}s (parallelism=${opts.parallelism})",
    )
}

private data class CliOptions(val sessionFolder: Path, val parallelism: Int)

private fun parseArgs(args: Array<String>): CliOptions? {
    var sessionFolder: Path? = null
    var parallelism: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

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

private fun saveNormalizedTraceToJson(trace: Trace): Path {
    // Dump the normalized stream to a scratch JSONL file next to this harness so we
    // can eyeball the full ordering without piping the console. Gitignored.
    val scratchPath = Path.of("src/main/kotlin/com/github/ethanhosier/analysis/cli/normalized-events.jsonl")
    val jsonl = Json { prettyPrint = false; encodeDefaults = true }
    Files.createDirectories(scratchPath.parent)
    Files.newBufferedWriter(scratchPath).use { w ->
        trace.events.forEach { event ->
            w.write(jsonl.encodeToString(TraceEvent.serializer(), event))
            w.newLine()
        }
    }
    return scratchPath
}
