package com.github.ethanhosier.ideplugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.github.ethanhosier.ideplugin.MyBundle

@Service(Service.Level.PROJECT)
class MyProjectService(project: Project) {

    init {
        thisLogger().info(MyBundle.message("projectService", project.name))
        thisLogger().warn("2. Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
        thisLogger().warn("Hello there!");
        thisLogger().info("Goodbye!")
    }

    fun getRandomNumber() = (1..100).random()
}
