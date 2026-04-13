package com.github.ethanhosier.ideplugin.services

import com.github.ethanhosier.ideplugin.model.Checkpoint
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Orchestrates checkpoint creation.
 *
 * Usage: call [createCheckpoint] with a trigger label (e.g. "REFACTORING_COMPLETED",
 * "MANUAL", "BUILD_FINISHED"). It will:
 *   1. Read dirty files + their current contents from FileStateTracker.
 *   2. Build a Checkpoint object.
 *   3. Register it with SessionService.
 *   4. Flush it to disk via StorageService.
 *   5. Reset FileStateTracker.
 */
@Service(Service.Level.PROJECT)
class CheckpointService(private val project: Project) {

    private val sequenceCounter = AtomicInteger(0)

    fun createCheckpoint(triggerType: String, activeFilePath: String? = null) {
        val sessionService = project.service<SessionService>()
        val fileStateTracker = project.service<FileStateTracker>()
        val storageService = project.service<StorageService>()

        val sessionId = sessionService.getSessionId()
        if (sessionId == null) {
            thisLogger().warn("RefactoringTracer: createCheckpoint called but no active session")
            return
        }

        val changedFiles = fileStateTracker.getDirtyFiles()
        val recentEventIds = sessionService.getRecentEventIds(20)
        val seq = sequenceCounter.incrementAndGet()

        val checkpoint = Checkpoint(
            id = UUID.randomUUID().toString(),
            sequenceNumber = seq,
            triggerType = triggerType,
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            recentEventIds = recentEventIds,
            changedFiles = changedFiles,
            activeFilePath = activeFilePath,
        )

        sessionService.addCheckpoint(checkpoint)
        storageService.flushCheckpoint(checkpoint)
        fileStateTracker.reset()

        thisLogger().info(
            "RefactoringTracer: checkpoint #$seq created trigger=$triggerType " +
            "files=${changedFiles.size} session=$sessionId"
        )
    }
}
