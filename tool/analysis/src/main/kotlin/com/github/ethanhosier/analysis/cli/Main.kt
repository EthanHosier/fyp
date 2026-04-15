package com.github.ethanhosier.analysis.cli

import com.github.ethanhosier.analysis.ingest.TraceLoader
import com.github.ethanhosier.analysis.normalize.TraceNormalizer
import com.github.ethanhosier.analysis.reconstruct.ShadowRepoBuilder
import com.github.ethanhosier.ideplugin.model.TraceEvent
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess
import com.github.ethanhosier.analysis.model.Trace

/**
 * Temporary harness used during Phase 1. Prints a summary of a loaded trace so we
 * can smoke-test `TraceLoader` against real session folders produced by the
 * ide-plugin. Will be replaced by a proper Clikt command in Step 6.
 */

/*
 ./gradlew :analysis:run --args="/Users/ethanhosier/IdeaProjects/untitled7/.refactoring-traces/98c45a19-16b9-43a7-9842-d6662c620d34" -q
 */

fun main(args: Array<String>) {
    if (args.size != 1) {
        System.err.println("usage: analysis <session-folder>")
        exitProcess(2)
    }

    val folder = Path.of(args[0])
    val raw = TraceLoader().load(folder)
    val trace = TraceNormalizer.normalize(raw)

    val reordered = raw.events.indices.count { i -> raw.events[i].id != trace.events[i].id }
    val scratchPath = saveNormalizedTraceToJson(trace)

    val result = ShadowRepoBuilder().build(folder, trace)
    val uniqueShas = result.eventCommits.mapping.values.toSet().size

    println("session:    ${trace.metadata.sessionId}")
    println("name:       ${trace.metadata.name}")
    println("project:    ${trace.metadata.projectName} (${trace.metadata.projectPath})")
    println("started:    ${trace.metadata.startTime}")
    println("ended:      ${trace.metadata.endTime}")
    println("events:     ${trace.events.size}")
    println("reordered:  $reordered event(s) moved by normalize")
    println("wrote:      ${scratchPath.toAbsolutePath()}")
    println("repo:       ${result.repoDir}")
    println("commits:    $uniqueShas unique (${trace.events.size - uniqueShas} events collapsed to prior SHA)")
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
