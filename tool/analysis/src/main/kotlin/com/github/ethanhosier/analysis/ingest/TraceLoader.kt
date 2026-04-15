package com.github.ethanhosier.analysis.ingest

import com.github.ethanhosier.analysis.model.Trace
import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.Session
import com.github.ethanhosier.ideplugin.model.TraceEvent
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads a trace from a session folder produced by the ide-plugin.
 *
 * Expects:
 *   <sessionFolder>/session.json   — full Session (metadata + events), written on clean session end
 *   <sessionFolder>/events.jsonl   — one JSON-encoded TraceEvent per line, appended live
 *
 * Missing `session.json` is fatal — a crashed session doesn't have a coherent
 * canonical form, so we don't try to recover one from the JSONL alone.
 *
 * As a consistency check the events in `session.json` are cross-referenced against
 * `events.jsonl`; any mismatch aborts the load. For a healthy session they are
 * constructed from the same in-memory list and should be identical.
 */
class TraceLoader(
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    fun load(sessionFolder: Path): Trace {
        require(Files.isDirectory(sessionFolder)) {
            "session folder does not exist or is not a directory: $sessionFolder"
        }

        val sessionJson = sessionFolder.resolve("session.json")
        require(Files.isRegularFile(sessionJson)) { "session.json missing at $sessionJson" }

        val eventsJsonl = sessionFolder.resolve("events.jsonl")
        require(Files.isRegularFile(eventsJsonl)) { "events.jsonl missing at $eventsJsonl" }

        val session = json.decodeFromString<Session>(Files.readString(sessionJson))

        val jsonlEvents = Files.readAllLines(eventsJsonl).mapIndexedNotNull { index, line ->
            if (line.isBlank()) return@mapIndexedNotNull null
            try {
                json.decodeFromString<TraceEvent>(line)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "failed to parse events.jsonl line ${index + 1}: ${e.message}",
                    e,
                )
            }
        }

        check(session.events == jsonlEvents) {
            "events.jsonl (${jsonlEvents.size} events) disagrees with session.json (${session.events.size} events)"
        }

        check(session.events.isNotEmpty()) { "trace has no events" }
        check(session.events.first().type == EventType.SESSION_STARTED) {
            "first event must be SESSION_STARTED but was ${session.events.first().type}"
        }
        check(session.events.last().type == EventType.SESSION_ENDED) {
            "last event must be SESSION_ENDED but was ${session.events.last().type}"
        }

        return Trace(session.metadata, session.events)
    }
}
