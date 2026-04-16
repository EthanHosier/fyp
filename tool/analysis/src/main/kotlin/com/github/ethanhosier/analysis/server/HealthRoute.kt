package com.github.ethanhosier.analysis.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String)

fun Route.health() {
    get("/health") {
        call.respond(HttpStatusCode.OK, HealthResponse(status = "ok"))
    }
}
