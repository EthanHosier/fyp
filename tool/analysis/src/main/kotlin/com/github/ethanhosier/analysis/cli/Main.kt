package com.github.ethanhosier.analysis.cli

import com.github.ethanhosier.analysis.ingest.TraceLoader
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Temporary harness used during Phase 1. Prints a summary of a loaded trace so we
 * can smoke-test `TraceLoader` against real session folders produced by the
 * ide-plugin. Will be replaced by a proper Clikt command in Step 6.
 */
fun main(args: Array<String>) {
    if (args.size != 1) {
        System.err.println("usage: analysis <session-folder>")
        exitProcess(2)
    }

    val folder = Path.of(args[0])
    val trace = TraceLoader().load(folder)

    println("session:  ${trace.metadata.sessionId}")
    println("name:     ${trace.metadata.name}")
    println("project:  ${trace.metadata.projectName} (${trace.metadata.projectPath})")
    println("started:  ${trace.metadata.startTime}")
    println("ended:    ${trace.metadata.endTime}")
    println("events:   ${trace.events.size}")
    println()
    println("first 5:")
    trace.events.take(5).forEach { println("  ${it.timestamp}  ${it.type}  ${it.id}") }
    println("last 5:")
    trace.events.takeLast(5).forEach { println("  ${it.timestamp}  ${it.type}  ${it.id}") }
}
