package com.github.ethanhosier.analysis.cli

import com.github.ethanhosier.analysis.pipeline.AnalysisPipeline
import com.github.ethanhosier.analysis.pipeline.PhaseAResult
import com.github.ethanhosier.analysis.refactoring.RefactoringClientFactory
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

object PhaseACli {
    private val writeJson = Json { prettyPrint = true; encodeDefaults = true }

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size != 2) {
            System.err.println("usage: phaseA <sessionFolder> <phaseA.json>")
            exitProcess(2)
        }
        val sessionFolder: Path = Paths.get(args[0])
        val outPath: Path = Paths.get(args[1])

        val started = System.currentTimeMillis()
        val dataArea = Files.createTempDirectory("analysis-equinox-").resolve("osgi")
        val refactoringClient = RefactoringClientFactory.create(dataArea)
        val phaseA = refactoringClient.use { rc ->
            AnalysisPipeline(refactoringClient = rc).runPhaseA(sessionFolder)
        }
        val durationMs = System.currentTimeMillis() - started

        Files.writeString(outPath, writeJson.encodeToString(PhaseAResult.serializer(), phaseA))
        println("phaseA: dumped to ${outPath.toAbsolutePath()} in ${durationMs / 1000}s")
    }
}

fun main(args: Array<String>) {
    PhaseACli.main(args)
}
