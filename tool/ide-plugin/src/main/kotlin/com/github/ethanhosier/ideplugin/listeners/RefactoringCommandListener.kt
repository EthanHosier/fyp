package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.services.RefactoringBurstCoordinator
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger

/**
 * Bridges the IntelliJ command boundary to [RefactoringBurstCoordinator]:
 *
 *  - `commandStarted` opens a synthesised envelope for refactoring commands
 *    that don't fire `RefactoringEventListener.refactoringStarted`
 *    (e.g. Change Method Signature, Move Method). Without this, the
 *    command's document mutations escape as a plain EDIT_BURST and the
 *    detector misclassifies the subsequent structural match as IDE_REPLAY.
 *  - `commandFinished` triggers REFACTORING_FINISHED emission for both
 *    platform-fired and synthesised envelopes, giving the trailing
 *    auto-format pass a chance to run first. The coordinator short-circuits
 *    if no refactoring is waiting, so this fires harmlessly for ordinary
 *    typing / paste / undo commands.
 *
 * Application-level listener: one instance covers every open project.
 * `CommandEvent.project` routes us to the right coordinator; null-project
 * commands (global IDE actions) are ignored.
 */
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
        // Refactoring commands observed to mutate documents without firing
        // RefactoringEventListener.refactoringStarted. Matching is case-insensitive
        // (IntelliJ uses inconsistent casing, e.g. "Move Instance method") and
        // tolerates trailing target identifiers (e.g. "Move method 'calc2'").
        // Expand as new gaps are identified — see plugin-misclassifications.md.
        // Refactoring commands observed to mutate documents either WITHOUT firing
        // RefactoringEventListener.refactoringStarted (Move Method / Change Method
        // Signature) OR firing it under a refactoringId the analyser's name-matching
        // doesn't bridge to RefactoringMiner's vocabulary (Extract/Introduce/Inline
        // operations — the synth envelope's friendly commandName matches RM cleaner
        // than the platform's `refactoring.extractVariable` style id). For the
        // second class, a transient duplicate envelope can briefly appear adjacent
        // to the platform one; the rework detector's between-refactor-nodes view
        // flags this as a known limitation rather than a wrong reading of the trace.
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
