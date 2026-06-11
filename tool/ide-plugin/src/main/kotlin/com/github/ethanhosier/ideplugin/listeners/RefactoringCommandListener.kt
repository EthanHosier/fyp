package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.services.RefactoringBurstCoordinator
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger

class RefactoringCommandListener : CommandListener {

    override fun commandStarted(event: CommandEvent) {
        val project = event.project ?: return
        val name = event.commandName
        thisLogger().warn("RefactoringTracer.commandStarted name=${name ?: "<null>"}")
        if (name.isNullOrBlank()) return
        if (!matchesRefactoring(name)) return
        val coordinator = project.service<RefactoringBurstCoordinator>()
        if (coordinator.isActive()) {
            thisLogger().warn("RefactoringTracer.commandStarted matched '$name' but coordinator already active — skipping synthesis")
            return
        }
        thisLogger().warn("RefactoringTracer.commandStarted synthesising envelope for '$name'")
        coordinator.beginSynthesisedRefactoring(refactoringId = name)
    }

    override fun commandFinished(event: CommandEvent) {
        val project = event.project ?: return
        project.service<RefactoringBurstCoordinator>().emitOnCommandFinish()
    }

    companion object {
        private val REFACTORING_NAME_PHRASES: Set<String> = setOf(
            "change method signature",
            "change signature",
            "move method",
            "move instance method",
            "move inner class",
            "move class",
            "pull members up",
            "push members down",
            "inline method",
            "inline variable",
            "inline field",
            "inline parameter",
            "safe delete",
            "extract method",
            "extract variable",
            "extract constant",
            "extract field",
            "extract parameter",
            "introduce variable",
            "introduce constant",
            "introduce field",
            "introduce parameter",
        )

        private fun matchesRefactoring(name: String): Boolean {
            val n = name.lowercase()
            return REFACTORING_NAME_PHRASES.any { phrase ->
                n == phrase || n.startsWith("$phrase ") || n.startsWith("$phrase'")
            }
        }
    }
}
