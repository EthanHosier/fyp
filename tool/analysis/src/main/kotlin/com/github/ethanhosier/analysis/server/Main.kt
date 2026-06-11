package com.github.ethanhosier.analysis.server

import com.github.ethanhosier.analysis.pipeline.AnalysisPipeline
import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringClientFactory
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path


private const val DEFAULT_PORT = 8080

fun main() {
    val port = System.getenv("ANALYSIS_SERVER_PORT")?.toIntOrNull() ?: DEFAULT_PORT
    embeddedServer(Netty, port = port) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    val refactoringClient = bootRefactoringClient()
    monitor.subscribe(ApplicationStopped) { refactoringClient.close() }
    val pipeline = AnalysisPipeline(refactoringClient = refactoringClient)

    install(ContentNegotiation) {
        json(Json { encodeDefaults = true })
    }
    routing {
        health()
        analyze(pipeline)
    }
}

private fun Application.bootRefactoringClient(): RefactoringClient {
    val dataArea: Path = Files.createTempDirectory("analysis-equinox-")
    val client = RefactoringClientFactory.create(dataArea.resolve("osgi"))
    log.info("RefactoringClient ready (data area: $dataArea)")
    return client
}
