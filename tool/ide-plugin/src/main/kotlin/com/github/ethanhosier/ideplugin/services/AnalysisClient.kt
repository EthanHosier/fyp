package com.github.ethanhosier.ideplugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Service(Service.Level.PROJECT)
class AnalysisClient {

    private val serverUrl: String =
        System.getenv("REFACTORING_TRACER_SERVER_URL") ?: "http://localhost:8080"

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    fun upload(sessionDir: Path): ByteArray {
        val sessionJson = sessionDir.resolve("session.json")
        require(Files.isRegularFile(sessionJson)) { "session.json missing at $sessionJson" }

        val initialSrc = sessionDir.resolve("initial-src")
        require(Files.isDirectory(initialSrc)) { "initial-src missing at $initialSrc" }

        val sessionBytes = Files.readAllBytes(sessionJson)
        val zipBytes = zipInitialSrc(initialSrc)
        val executablePathsJson = executablePathsJson(initialSrc)

        val boundary = "----RTBoundary${UUID.randomUUID()}"
        val body = buildMultipart(
            boundary = boundary,
            parts = listOf(
                Part("session.json", "application/json", sessionBytes),
                Part("initial-src.zip", "application/zip", zipBytes),
                Part("executable-paths.json", "application/json", executablePathsJson),
            ),
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$serverUrl/analyze"))
            .timeout(Duration.ofMinutes(10))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        thisLogger().info(
            "RefactoringTracer: POST $serverUrl/analyze " +
                "session=${sessionBytes.size}B zip=${zipBytes.size}B"
        )
        val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) {
            val preview = String(response.body()).take(500)
            error("analysis server returned ${response.statusCode()}: $preview")
        }
        return response.body()
    }

    private fun zipInitialSrc(root: Path): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val rel = root.relativize(file).toString().replace('\\', '/')
                    zip.putNextEntry(ZipEntry(rel))
                    Files.copy(file, zip)
                    zip.closeEntry()
                    return FileVisitResult.CONTINUE
                }
            })
        }
        return buffer.toByteArray()
    }

    private fun executablePathsJson(root: Path): ByteArray {
        val paths = mutableListOf<String>()
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (Files.isExecutable(file)) {
                    paths += root.relativize(file).toString().replace('\\', '/')
                }
                return FileVisitResult.CONTINUE
            }
        })
        val escaped = paths.joinToString(",") { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }
        return "[$escaped]".toByteArray()
    }

    private data class Part(val name: String, val contentType: String, val bytes: ByteArray)

    private fun buildMultipart(boundary: String, parts: List<Part>): ByteArray {
        val out = ByteArrayOutputStream()
        for (part in parts) {
            out.writeAscii("--$boundary\r\n")
            out.writeAscii(
                "Content-Disposition: form-data; name=\"${part.name}\"; filename=\"${part.name}\"\r\n"
            )
            out.writeAscii("Content-Type: ${part.contentType}\r\n\r\n")
            out.write(part.bytes)
            out.writeAscii("\r\n")
        }
        out.writeAscii("--$boundary--\r\n")
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeAscii(s: String) = write(s.toByteArray(Charsets.US_ASCII))
}
