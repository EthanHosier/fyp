package com.github.ethanhosier.analysis.server

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

/**
 * HTTP entrypoint for the analysis pipeline. Sibling to `cli` — same
 * module, same classpath, different invocation. Eventually this will
 * run remotely; for now it sits next to the IDE and accepts uploads
 * from the ide-plugin at session end.
 *
 * Run: `./gradlew :analysis:runServer`
 */

private const val DEFAULT_PORT = 8080

fun main() {
    val port = System.getenv("ANALYSIS_SERVER_PORT")?.toIntOrNull() ?: DEFAULT_PORT
    embeddedServer(Netty, port = port) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    // Pay the Equinox + JDT boot cost once at server start. The
    // resulting client is shared across every request that needs to
    // run a refactoring (currently none; the future AlternativeTrajectory
    // pipeline stage will consume it).
    val refactoringClient = bootRefactoringClient()
    monitor.subscribe(ApplicationStopped) { refactoringClient.close() }

    install(ContentNegotiation) {
        json(Json { encodeDefaults = true })
    }
    routing {
        health()
        analyze()
    }
}

private fun Application.bootRefactoringClient(): RefactoringClient {
    val dataArea: Path = Files.createTempDirectory("analysis-equinox-")
    val client = RefactoringClientFactory.create(dataArea.resolve("osgi"))
    log.info("RefactoringClient ready (data area: $dataArea)")
    return client
}
