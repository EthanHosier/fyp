package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.services.CheckpointService
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
        project.service<SessionService>().addEvent(
            EventType.REFACTORING_FINISHED,
            payload = mapOf("refactoringId" to refactoringId, "outcome" to "done"),
        )
        project.service<CheckpointService>().createCheckpoint("REFACTORING_COMPLETED")
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
