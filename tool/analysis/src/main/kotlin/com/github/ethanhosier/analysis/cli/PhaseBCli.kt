package com.github.ethanhosier.analysis.cli

import com.github.ethanhosier.analysis.metrics.derived.ScoringConfig
import com.github.ethanhosier.analysis.pipeline.AnalysisReport
import com.github.ethanhosier.analysis.pipeline.PhaseAResult
import com.github.ethanhosier.analysis.pipeline.ReportAssembler
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Cheap Phase-B replay: given a serialised [PhaseAResult] dump and a
 * JSON [ScoringConfig], reassemble an [AnalysisReport] without
 * re-running the expensive Phase-A pipeline. Intended for tuning
 * scoring weights / derived-metric logic against cached Phase-A
 * artefacts.
 */
object PhaseBCli {
    private val readJson = Json { ignoreUnknownKeys = true; isLenient = true }
    // Mirror `Main.kt`'s `reportJson` so report files written by either
    // entrypoint render identically (pretty-printed, defaults included).
    private val writeJson = Json { prettyPrint = true; encodeDefaults = true }

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size != 3) {
            System.err.println("usage: phaseB <phaseA.json> <config.json> <outputReport.json>")
            exitProcess(2)
        }
        val phaseAPath = Paths.get(args[0])
        val configPath = Paths.get(args[1])
        val outPath = Paths.get(args[2])

        val phaseA = readJson.decodeFromString(
            PhaseAResult.serializer(),
            Files.readString(phaseAPath),
        )
        val config = readJson.decodeFromString(
            ScoringConfig.serializer(),
            Files.readString(configPath),
        )

        val started = System.currentTimeMillis()
        val report = ReportAssembler.assemble(phaseA, config)
        val durationMs = System.currentTimeMillis() - started

        Files.writeString(outPath, writeJson.encodeToString(AnalysisReport.serializer(), report))
        println("phaseB: assembled report at ${outPath.toAbsolutePath()} in ${durationMs}ms")
    }
}

fun main(args: Array<String>) {
    PhaseBCli.main(args)
}
