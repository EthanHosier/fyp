package com.github.ethanhosier.ideplugin.actions

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.services.SessionService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages

class StartTaskAction : AnAction("Start Task") {

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null &&
            project.service<SessionService>().getActiveTaskLabel() == null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val label = Messages.showInputDialog(
            project,
            "Task label:",
            "Start Task",
            Messages.getQuestionIcon(),
        ) ?: return  // user cancelled

        project.service<SessionService>().addEvent(
            type = EventType.TASK_STARTED,
            payload = mapOf("label" to label),
        )
    }
}
