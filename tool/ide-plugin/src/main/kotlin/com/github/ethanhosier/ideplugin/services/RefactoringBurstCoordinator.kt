package com.github.ethanhosier.ideplugin.services

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.FileChangeType
import com.github.ethanhosier.ideplugin.model.FileSnapshot
import com.github.ethanhosier.ideplugin.refactoring.RefactoringTypeRegistry
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.refactoring.listeners.RefactoringEventData

@Service(Service.Level.PROJECT)
class RefactoringBurstCoordinator(private val project: Project) {

    private val lock = Any()
    private var activeRefactoringId: String? = null
    private var activeBeforeData: RefactoringEventData? = null
    private var pendingAfterData: RefactoringEventData? = null
    private var awaitingCommandFinish: Boolean = false
    // path -> latest in-memory document text observed during the refactoring.
    // LinkedHashMap so the emitted changedFiles preserve first-touched order.
    private val accumulator: LinkedHashMap<String, String> = LinkedHashMap()

    fun isActive(): Boolean = synchronized(lock) { activeRefactoringId != null }

    fun beginRefactoring(refactoringId: String, beforeData: RefactoringEventData? = null) {
        synchronized(lock) {
            if (activeRefactoringId != null) {
                thisLogger().warn(
                    "RefactoringTracer: beginRefactoring($refactoringId) while " +
                    "$activeRefactoringId already active — closing previous as 'superseded'"
                )
                emitFinishedLocked(outcome = "superseded", refactoringId = activeRefactoringId!!, afterData = pendingAfterData)
            }
            // Flush any pending edit burst so the pre-refactoring state is emitted as
            // its own EDIT_BURST before we start intercepting further changes.
            project.service<EditBurstTracker>().flushAllPending()
            activeRefactoringId = refactoringId
            activeBeforeData = beforeData
            pendingAfterData = null
            awaitingCommandFinish = false
            accumulator.clear()
        }
        project.service<SessionService>().addEvent(
            EventType.REFACTORING_STARTED,
            payload = mapOf("refactoringId" to refactoringId),
        )
    }

    fun beginSynthesisedRefactoring(refactoringId: String) {
        synchronized(lock) {
            if (activeRefactoringId != null) return
            project.service<EditBurstTracker>().flushAllPending()
            activeRefactoringId = refactoringId
            activeBeforeData = null
            pendingAfterData = null
            awaitingCommandFinish = true
            accumulator.clear()
        }
        project.service<SessionService>().addEvent(
            EventType.REFACTORING_STARTED,
            payload = mapOf("refactoringId" to refactoringId, "synthesised" to "true"),
        )
    }

    fun markRefactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
        synchronized(lock) {
            val active = activeRefactoringId ?: return
            if (refactoringId != active) {
                thisLogger().warn(
                    "RefactoringTracer: markRefactoringDone($refactoringId) but active is $active — ignoring"
                )
                return
            }
            pendingAfterData = afterData
            awaitingCommandFinish = true
        }
    }

    fun emitOnCommandFinish() {
        synchronized(lock) {
            if (!awaitingCommandFinish) return
            val active = activeRefactoringId ?: return
            emitFinishedLocked(outcome = "done", refactoringId = active, afterData = pendingAfterData)
        }
    }

    fun endRefactoringNow(
        refactoringId: String?,
        outcome: String,
        afterData: RefactoringEventData? = null,
    ) {
        synchronized(lock) {
            val active = activeRefactoringId ?: return
            if (refactoringId != null && refactoringId != active) {
                thisLogger().warn(
                    "RefactoringTracer: endRefactoringNow($refactoringId) but active is $active — ending anyway"
                )
            }
            emitFinishedLocked(outcome = outcome, refactoringId = active, afterData = afterData)
        }
    }

    private fun emitFinishedLocked(
        outcome: String,
        refactoringId: String,
        afterData: RefactoringEventData?,
    ) {
        val handlerSnapshots = ApplicationManager.getApplication().runReadAction<List<FileSnapshot>> {
            RefactoringTypeRegistry.handlerFor(refactoringId)
                .snapshotsFor(project, activeBeforeData, afterData)
        }
        val merged = LinkedHashMap<String, FileSnapshot>()
        for (snap in handlerSnapshots) merged[snap.path] = snap
        for ((path, contents) in accumulator) {
            val existing = merged[path]
            merged[path] = if (existing != null) {
                existing.copy(contents = contents)
            } else {
                FileSnapshot(path, contents, FileChangeType.MODIFIED)
            }
        }

        activeRefactoringId = null
        activeBeforeData = null
        pendingAfterData = null
        awaitingCommandFinish = false
        accumulator.clear()

        project.service<SessionService>().addEvent(
            EventType.REFACTORING_FINISHED,
            changedFiles = merged.values.toList(),
            payload = mapOf("refactoringId" to refactoringId, "outcome" to outcome),
        )
    }

    fun onDocumentChanged(vFile: VirtualFile, event: DocumentEvent): Boolean {
        synchronized(lock) {
            if (activeRefactoringId == null) return false
            accumulator[vFile.path] = event.document.text
            return true
        }
    }

    fun flushIfActive(outcome: String) {
        endRefactoringNow(refactoringId = null, outcome = outcome)
    }
}
