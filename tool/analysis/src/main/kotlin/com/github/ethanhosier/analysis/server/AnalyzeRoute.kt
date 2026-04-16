package com.github.ethanhosier.analysis.server

import com.github.ethanhosier.analysis.pipeline.AnalysisPipeline
import com.github.ethanhosier.ideplugin.model.Session
import com.github.ethanhosier.ideplugin.model.TraceEvent
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.absolute

private val pipelineJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@kotlinx.serialization.Serializable
private data class ErrorResponse(val error: String, val message: String)

fun Route.analyze(pipeline: AnalysisPipeline = AnalysisPipeline()) {
    post("/analyze") {
        val tempDir = Files.createTempDirectory("analysis-")
        try {
            receiveUpload(call.receiveMultipart(), tempDir)
            val result = pipeline.run(tempDir)
            call.respond(HttpStatusCode.OK, result.report)
        } catch (e: BadUploadException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = "bad_upload", message = e.message ?: "invalid upload"),
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(error = "pipeline_failed", message = e.message ?: e.toString()),
            )
        } finally {
            runCatching { tempDir.toFile().deleteRecursively() }
        }
    }
}

private class BadUploadException(message: String) : RuntimeException(message)

private suspend fun receiveUpload(multipart: MultiPartData, tempDir: Path) {
    val initialSrc = tempDir.resolve("initial-src").also { Files.createDirectories(it) }

    var sawSession = false
    var sawZip = false
    var executablePaths: List<String> = emptyList()

    multipart.forEachPart { part ->
        try {
            when (part) {
                is PartData.FileItem -> when (part.name) {
                    "session.json" -> {
                        val bytes = part.provider().toInputStream().use(InputStream::readAllBytes)
                        writeSessionArtefacts(tempDir, bytes)
                        sawSession = true
                    }
                    "initial-src.zip" -> {
                        part.provider().toInputStream().use { unzipInto(it, initialSrc) }
                        sawZip = true
                    }
                    "executable-paths.json" -> {
                        val bytes = part.provider().toInputStream().use(InputStream::readAllBytes)
                        executablePaths = pipelineJson.decodeFromString(String(bytes))
                    }
                    else -> throw BadUploadException("unexpected part: ${part.name}")
                }
                else -> throw BadUploadException("expected file part but got ${part::class.simpleName}")
            }
        } finally {
            part.dispose()
        }
    }

    if (!sawSession) throw BadUploadException("missing part: session.json")
    if (!sawZip) throw BadUploadException("missing part: initial-src.zip")

    applyExecutableBits(initialSrc, executablePaths)
}

private fun writeSessionArtefacts(tempDir: Path, sessionJsonBytes: ByteArray) {
    Files.write(tempDir.resolve("session.json"), sessionJsonBytes)

    // TraceLoader also needs events.jsonl — derive it from the session so the
    // plugin doesn't have to upload the same events twice in two formats.
    val session = pipelineJson.decodeFromString<Session>(String(sessionJsonBytes))
    val eventsJsonl = tempDir.resolve("events.jsonl")
    Files.newBufferedWriter(eventsJsonl).use { writer ->
        for (event in session.events) {
            writer.write(pipelineJson.encodeToString(TraceEvent.serializer(), event))
            writer.newLine()
        }
    }
}

private fun unzipInto(input: InputStream, destRoot: Path) {
    val canonicalRoot = destRoot.absolute().normalize()
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            val resolved = destRoot.resolve(entry.name).absolute().normalize()
            if (!resolved.startsWith(canonicalRoot)) {
                throw BadUploadException("zip entry escapes destination: ${entry.name}")
            }
            if (entry.isDirectory) {
                Files.createDirectories(resolved)
            } else {
                Files.createDirectories(resolved.parent)
                Files.newOutputStream(resolved).use { out -> zip.copyTo(out) }
            }
            zip.closeEntry()
        }
    }
}

private fun applyExecutableBits(initialSrc: Path, relativePaths: List<String>) {
    val canonicalRoot = initialSrc.absolute().normalize()
    for (rel in relativePaths) {
        val resolved = initialSrc.resolve(rel).absolute().normalize()
        if (!resolved.startsWith(canonicalRoot)) {
            throw BadUploadException("executable path escapes initial-src: $rel")
        }
        if (Files.isRegularFile(resolved)) {
            resolved.toFile().setExecutable(true, false)
        }
    }
}

