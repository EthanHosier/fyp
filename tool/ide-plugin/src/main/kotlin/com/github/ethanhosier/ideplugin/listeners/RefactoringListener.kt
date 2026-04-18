package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.services.RefactoringBurstCoordinator
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener

/**
 * Bridges IntelliJ's RefactoringEventListener topic into [RefactoringBurstCoordinator],
 * which owns the `in-refactoring` flag, collects changed file contents during the
 * refactoring, and emits REFACTORING_STARTED / REFACTORING_FINISHED.
 *
 * Modal refactorings (Rename dialog, Move dialog, Change Signature, etc.) reliably
 * produce one of `refactoringDone` / `conflictsDetected` / `undoRefactoring` as a
 * terminator. In-place / preview refactorings do not — they are terminated via
 * [RefactoringTemplateListener] instead. The coordinator handles either source
 * idempotently.
 */
class RefactoringListener(private val project: Project) : RefactoringEventListener {

    override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {
        project.service<RefactoringBurstCoordinator>().beginRefactoring(refactoringId, beforeData)
    }

    override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
        project.service<RefactoringBurstCoordinator>()
            .endRefactoring(refactoringId, outcome = "done", afterData = afterData)
    }

    override fun conflictsDetected(refactoringId: String, conflictsData: RefactoringEventData) {
        project.service<RefactoringBurstCoordinator>().endRefactoring(refactoringId, outcome = "conflicts")
    }

    override fun undoRefactoring(refactoringId: String) {
        project.service<RefactoringBurstCoordinator>().endRefactoring(refactoringId, outcome = "undone")
    }
}
