package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.services.SessionService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener

class RefactoringListener(private val project: Project) : RefactoringEventListener {

    override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) =
        project.service<SessionService>().addEvent(
            EventType.REFACTORING_STARTED,
            payload = mapOf("refactoringId" to refactoringId),
        )

    override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
        // File contents are NOT captured here. Every file touched by the refactoring emits its
        // own VFS event (FILE_RENAMED, FILE_MOVED, FILE_CREATED, or a content-change caught by
        // EditBurstTracker) which records the full post-refactoring contents inline. To
        // reconstruct what changed during a refactoring, join on sessionId + refactoringId and
        // collect all VFS / EDIT_BURST events within a short timestamp window of this event.
        //
        // Limitation: the VFS events fire asynchronously — there is no guaranteed ordering
        // between this REFACTORING_FINISHED event and the surrounding VFS events. Use a
        // timestamp window (e.g. ±500 ms) rather than strict ordering when correlating at
        // analysis time.
        project.service<SessionService>().addEvent(
            EventType.REFACTORING_FINISHED,
            payload = mapOf("refactoringId" to refactoringId, "outcome" to "done"),
        )
    }

    override fun conflictsDetected(refactoringId: String, conflictsData: RefactoringEventData) =
        project.service<SessionService>().addEvent(
            EventType.REFACTORING_FINISHED,
            payload = mapOf("refactoringId" to refactoringId, "outcome" to "conflicts"),
        )

    override fun undoRefactoring(refactoringId: String) =
        project.service<SessionService>().addEvent(
            EventType.REFACTORING_FINISHED,
            payload = mapOf("refactoringId" to refactoringId, "outcome" to "undone"),
        )
}
