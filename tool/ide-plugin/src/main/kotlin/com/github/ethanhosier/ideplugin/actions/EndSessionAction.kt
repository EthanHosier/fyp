package com.github.ethanhosier.ideplugin.actions

import com.github.ethanhosier.ideplugin.services.SessionService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class EndSessionAction : AnAction("End Session") {

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null &&
            project.service<SessionService>().isSessionActive()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<SessionService>().endSession()
    }
}
