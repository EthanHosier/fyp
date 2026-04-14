package com.github.ethanhosier.ideplugin.actions

import com.github.ethanhosier.ideplugin.services.SessionService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages

class StartSessionAction : AnAction("Start Session") {

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null &&
            !project.service<SessionService>().isSessionActive()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val label = Messages.showInputDialog(
            project,
            "Session name:",
            "Start Session",
            Messages.getQuestionIcon(),
        ) ?: return  // user cancelled

        project.service<SessionService>().startSession(label)
    }
}
