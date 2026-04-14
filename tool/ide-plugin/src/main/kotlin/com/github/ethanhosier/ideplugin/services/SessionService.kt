package com.github.ethanhosier.ideplugin.services

import com.github.ethanhosier.ideplugin.model.*
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.util.UUID

@Service(Service.Level.PROJECT)
class SessionService(private val project: Project) {

    @Volatile private var metadata: SessionMetadata? = null

    // Guards every read/write of `events`. Plain ArrayList + lock is O(1) amortised
    // per add; CopyOnWriteArrayList would copy the whole backing array on each add
    // (O(n²) over a long session).
    private val eventsLock = Any()
    private val events: MutableList<TraceEvent> = ArrayList()

    fun startSession(name: String) {
        if (isSessionActive()) {
            thisLogger().warn("RefactoringTracer: startSession called while a session is already active — ignoring")
            return
        }
        synchronized(eventsLock) { events.clear() }

        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val (branch, commit) = readGitInfo()

        val appInfo = ApplicationInfo.getInstance()
        val ideVersion = "${appInfo.versionName} ${appInfo.fullVersion}"
        val pluginVersion = javaClass.`package`?.implementationVersion ?: "dev"

        metadata = SessionMetadata(
            sessionId = sessionId,
            name = name,
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
        synchronized(eventsLock) { events.add(sessionStartedEvent) }
        project.service<StorageService>().init(sessionId, project.basePath ?: "")
        project.service<StorageService>().flushEvent(sessionStartedEvent)
    }

    fun endSession() {
        val meta = metadata ?: return

        // Drain pending edit bursts before marking the session ended — otherwise
        // addEvent() would reject the flushed bursts as post-session events.
        project.service<EditBurstTracker>().flushAllPending()

        val now = System.currentTimeMillis()
        metadata = meta.copy(endTime = now)

        val endEvent = TraceEvent(
            id = UUID.randomUUID().toString(),
            type = EventType.SESSION_ENDED,
            timestamp = now,
            sessionId = meta.sessionId,
        )
        synchronized(eventsLock) { events.add(endEvent) }
        project.service<StorageService>().flushEvent(endEvent)

        val session = getSession() ?: return
        project.service<StorageService>().flushSession(session)
        thisLogger().info("RefactoringTracer: session ended id=${meta.sessionId}")
    }

    fun addEvent(event: TraceEvent) {
        if (!isSessionActive()) return  // no active session — drop event
        synchronized(eventsLock) { events.add(event) }
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

    fun isSessionActive(): Boolean = metadata != null && metadata?.endTime == null
    fun getSessionName(): String? = metadata?.name

    fun getSession(): Session? {
        val meta = metadata ?: return null
        val snapshot = synchronized(eventsLock) { events.toList() }
        return Session(
            metadata = meta,
            events = snapshot,
        )
    }

    fun getSessionId(): String? = metadata?.sessionId
    fun getStartTime(): Long? = metadata?.startTime
    fun getEventCount(): Int = synchronized(eventsLock) { events.size }

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
        // stdout and stderr kept separate so a non-git project (which prints
        // "fatal: not a git repository" on stderr) doesn't end up stored as
        // the branch name. Only trust stdout when the process exits cleanly.
        val process = ProcessBuilder("git", *args)
            .directory(java.io.File(workDir))
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        if (exitCode != 0) return null
        return output.ifBlank { null }
    }
}
