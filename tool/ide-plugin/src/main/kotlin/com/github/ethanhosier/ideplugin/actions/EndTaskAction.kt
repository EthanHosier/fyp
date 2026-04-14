package com.github.ethanhosier.ideplugin.actions

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.services.SessionService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class EndTaskAction : AnAction("End Task") {

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null &&
            project.service<SessionService>().getActiveTaskLabel() != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val sessionService = project.service<SessionService>()
        val label = sessionService.getActiveTaskLabel() ?: return
        sessionService.addEvent(
            type = EventType.TASK_ENDED,
            payload = mapOf("label" to label),
        )
    }
}
