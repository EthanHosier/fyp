package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.services.SessionService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

class ProjectCloseListener : ProjectManagerListener {

    override fun projectClosing(project: Project) {
        project.service<SessionService>().endSession()
    }
}
