package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.services.CheckpointService
import com.github.ethanhosier.ideplugin.services.SessionService
import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.UUID

class BuildListener : BuildManagerListener {

    override fun buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean) =
        project.service<SessionService>().addEvent(
            EventType.BUILD_STARTED,
            payload = mapOf("isAutomake" to isAutomake.toString()),
        )

    override fun buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean) {
        project.service<SessionService>().addEvent(
            EventType.BUILD_FINISHED,
            payload = mapOf("isAutomake" to isAutomake.toString()),
        )
        if (!isAutomake) {
            project.service<CheckpointService>().createCheckpoint("BUILD_FINISHED")
        }
    }
}
