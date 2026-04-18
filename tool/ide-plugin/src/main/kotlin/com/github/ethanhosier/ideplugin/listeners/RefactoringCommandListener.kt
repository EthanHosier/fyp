package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.services.RefactoringBurstCoordinator
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.components.service

/**
 * Triggers REFACTORING_FINISHED emission when the command that contains a
 * refactoring closes — giving the trailing auto-format pass a chance to
 * run first. The coordinator short-circuits if no refactoring is waiting,
 * so this fires harmlessly for ordinary typing / paste / undo commands.
 *
 * Application-level listener: one instance covers every open project.
 * `CommandEvent.project` routes us to the right coordinator; null-project
 * commands (global IDE actions) are ignored.
 */
class RefactoringCommandListener : CommandListener {

    override fun commandFinished(event: CommandEvent) {
        val project = event.project ?: return
        project.service<RefactoringBurstCoordinator>().emitOnCommandFinish()
    }
}
