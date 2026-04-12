package com.github.ethanhosier.ideplugin.listeners

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener

class RefactoringListener : RefactoringEventListener {

    override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {
        thisLogger().info("[STUB] Refactoring started: id=$refactoringId")
    }

    override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
        thisLogger().info("[STUB] Refactoring done: id=$refactoringId")
    }

    override fun conflictsDetected(refactoringId: String, conflictsData: RefactoringEventData) {
        thisLogger().info("[STUB] Refactoring conflicts detected: id=$refactoringId")
    }

    override fun undoRefactoring(refactoringId: String) {
        thisLogger().info("[STUB] Refactoring undone: id=$refactoringId")
    }
}
