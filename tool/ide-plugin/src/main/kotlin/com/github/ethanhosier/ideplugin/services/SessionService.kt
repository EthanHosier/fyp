package com.github.ethanhosier.ideplugin.services

import com.github.ethanhosier.ideplugin.model.*
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class SessionService(private val project: Project) {

    private var metadata: SessionMetadata? = null
    private val events: CopyOnWriteArrayList<TraceEvent> = CopyOnWriteArrayList()
    private var activeTaskLabel: String? = null

    fun startSession() {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val (branch, commit) = readGitInfo()

        val appInfo = ApplicationInfo.getInstance()
        val ideVersion = "${appInfo.versionName} ${appInfo.fullVersion}"
        val pluginVersion = javaClass.`package`?.implementationVersion ?: "dev"

        metadata = SessionMetadata(
            sessionId = sessionId,
            projectName = project.name,
            projectPath = project.basePath ?: "",
            branch = branch,
            commitHash = commit,
            startTime = now,
            ideVersion = ideVersion,
            pluginVersion = pluginVersion,
        )

        thisLogger().info("RefactoringTracer: session started id=$sessionId project=${project.name}")

        val sessionStartedEvent = TraceEvent(
            id = UUID.randomUUID().toString(),
            type = EventType.SESSION_STARTED,
            timestamp = now,
            sessionId = sessionId,
        )
        events.add(sessionStartedEvent)
        project.service<StorageService>().init(sessionId, project.basePath ?: "")
        project.service<StorageService>().flushEvent(sessionStartedEvent)
    }

    fun endSession() {
        val meta = metadata ?: return
        val now = System.currentTimeMillis()
        metadata = meta.copy(endTime = now)

        val endEvent = TraceEvent(
            id = UUID.randomUUID().toString(),
            type = EventType.SESSION_ENDED,
            timestamp = now,
            sessionId = meta.sessionId,
        )
        events.add(endEvent)
        project.service<StorageService>().flushEvent(endEvent)

        val session = getSession() ?: return
        project.service<StorageService>().flushSession(session)
        thisLogger().info("RefactoringTracer: session ended id=${meta.sessionId}")
    }

    fun addEvent(event: TraceEvent) {
        if (metadata == null) return  // session not started yet
        events.add(event)
        project.service<StorageService>().flushEvent(event)
    }

    /** Convenience overload: builds and records a TraceEvent without the caller needing to manage IDs or timestamps. */
    fun addEvent(
        type: EventType,
        changedFiles: List<FileSnapshot> = emptyList(),
        relatedFiles: List<String> = emptyList(),
        payload: Map<String, String> = emptyMap(),
    ) {
        val sessionId = metadata?.sessionId ?: return
        when (type) {
            EventType.TASK_STARTED -> activeTaskLabel = payload["label"]
            EventType.TASK_ENDED -> activeTaskLabel = null
            else -> {}
        }
        addEvent(
            TraceEvent(
                id = UUID.randomUUID().toString(),
                type = type,
                timestamp = System.currentTimeMillis(),
                sessionId = sessionId,
                changedFiles = changedFiles,
                relatedFiles = relatedFiles,
                payload = payload,
            )
        )
    }

    fun getActiveTaskLabel(): String? = activeTaskLabel

    fun getSession(): Session? {
        val meta = metadata ?: return null
        return Session(
            metadata = meta,
            events = events.toList(),
        )
    }

    fun getSessionId(): String? = metadata?.sessionId
    fun getStartTime(): Long? = metadata?.startTime
    fun getEventCount(): Int = events.size

    // --- private helpers ---

    private fun readGitInfo(): Pair<String?, String?> {
        val dir = project.basePath ?: return null to null
        return try {
            val branch = runGit(dir, "rev-parse", "--abbrev-ref", "HEAD")
            val commit = runGit(dir, "rev-parse", "--short", "HEAD")
            branch to commit
        } catch (e: Exception) {
            thisLogger().warn("RefactoringTracer: could not read git info: ${e.message}")
            null to null
        }
    }

    private fun runGit(workDir: String, vararg args: String): String? {
        val process = ProcessBuilder("git", *args)
            .directory(java.io.File(workDir))
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        return output.ifBlank { null }
    }
}
