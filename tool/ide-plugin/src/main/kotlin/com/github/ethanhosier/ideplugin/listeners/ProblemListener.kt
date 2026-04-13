package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.services.ProblemDebouncer
import com.github.ethanhosier.ideplugin.util.isTracesFile
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.ProblemListener

class ProblemListener(private val project: Project) : ProblemListener {

    override fun problemsAppeared(file: VirtualFile) = dispatch(file, hasErrors = true)
    override fun problemsChanged(file: VirtualFile) = dispatch(file, hasErrors = true)
    override fun problemsDisappeared(file: VirtualFile) = dispatch(file, hasErrors = false)

    private fun dispatch(file: VirtualFile, hasErrors: Boolean) {
        if (file.isTracesFile()) return
        project.service<ProblemDebouncer>().onProblemsChanged(file, hasErrors)
    }
}
