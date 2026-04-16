package com.github.ethanhosier.ideplugin.services

import com.github.ethanhosier.ideplugin.model.*
import com.github.ethanhosier.ideplugin.util.SnapshotFilter
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
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
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            thisLogger().warn("RefactoringTracer: startSession called with blank name — ignoring")
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
            name = trimmedName,
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
        snapshotSource("initial-src")
        project.service<StorageService>().flushEvent(sessionStartedEvent)
    }

    /**
     * Copies every capturable file under the project root into
     * `<sessionDir>/<subdir>/`, preserving the tree structure relative to the
     * project base path.
     *
     * Filtering goes through [SnapshotFilter]: a hardcoded skip list for
     * generated output, IDE/VCS metadata, dependency caches, and an
     * extension/size cap. This captures not just sources but also build
     * config (build.gradle*, settings.gradle*, gradle.properties) and the
     * Gradle wrapper (gradlew, gradle/wrapper/) — all of which the analysis
     * pipeline needs to run `./gradlew` on a reconstructed checkpoint.
     *
     * The baseline snapshot at session start is what the analysis tool uses
     * to reconstruct edit history via a shadow git repo. The same mechanism
     * can later be reused for mid-session checkpoints.
     */
    private fun snapshotSource(subdir: String) {
        val basePath = project.basePath ?: run {
            thisLogger().warn("RefactoringTracer: project has no basePath — skipping '$subdir' snapshot")
            return
        }
        val basePathNio = Path.of(basePath)
        if (!Files.isDirectory(basePathNio)) {
            thisLogger().warn("RefactoringTracer: basePath $basePath is not a directory — skipping '$subdir' snapshot")
            return
        }

        val storage = project.service<StorageService>()
        var count = 0
        var skipped = 0

        Files.walkFileTree(basePathNio, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir == basePathNio) return FileVisitResult.CONTINUE
                if (SnapshotFilter.shouldSkipDir(dir.fileName.toString())) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val rel = basePathNio.relativize(file)
                if (!SnapshotFilter.shouldCaptureFile(rel, attrs.size())) {
                    skipped++
                    return FileVisitResult.CONTINUE
                }
                try {
                    storage.writeSessionFile(
                        relativePath = "$subdir/$rel",
                        bytes = Files.readAllBytes(file),
                        executable = Files.isExecutable(file),
                    )
                    count++
                } catch (e: Exception) {
                    thisLogger().warn("RefactoringTracer: failed to snapshot $file: ${e.message}")
                }
                return FileVisitResult.CONTINUE
            }
        })

        thisLogger().info("RefactoringTracer: '$subdir' snapshot wrote $count files ($skipped filtered)")
    }

    fun endSession() {
        val meta = metadata ?: return
        if (meta.endTime != null) return

        // Close any in-flight refactoring first so its accumulated state is emitted
        // as REFACTORING_FINISHED before we tear down the session. Then drain pending
        // edit bursts — otherwise addEvent() would reject the flushed events as
        // post-session.
        project.service<RefactoringBurstCoordinator>().flushIfActive(outcome = "session_ended")
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
