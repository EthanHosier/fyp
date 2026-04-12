package com.github.ethanhosier.ideplugin.listeners

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.util.UUID

class BuildListener : BuildManagerListener {

    override fun buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean) {
        thisLogger().info("[STUB] Build started: project=${project.name} sessionId=$sessionId isAutomake=$isAutomake")
    }

    override fun buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean) {
        thisLogger().info("[STUB] Build finished: project=${project.name} sessionId=$sessionId isAutomake=$isAutomake")
    }
}
