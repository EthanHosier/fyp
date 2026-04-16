package com.github.ethanhosier.analysis.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

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
    install(ContentNegotiation) {
        json(Json { encodeDefaults = true })
    }
    routing {
        health()
        analyze()
    }
}
