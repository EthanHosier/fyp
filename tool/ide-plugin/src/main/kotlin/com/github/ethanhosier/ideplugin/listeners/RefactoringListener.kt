package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.services.RefactoringBurstCoordinator
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener

class RefactoringListener(private val project: Project) : RefactoringEventListener {

    override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {
        project.service<RefactoringBurstCoordinator>().beginRefactoring(refactoringId, beforeData)
    }

    override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
        project.service<RefactoringBurstCoordinator>().markRefactoringDone(refactoringId, afterData)
    }

    override fun conflictsDetected(refactoringId: String, conflictsData: RefactoringEventData) {
        project.service<RefactoringBurstCoordinator>().endRefactoringNow(refactoringId, outcome = "conflicts")
    }

    override fun undoRefactoring(refactoringId: String) {
        project.service<RefactoringBurstCoordinator>().endRefactoringNow(refactoringId, outcome = "undone")
    }
}
