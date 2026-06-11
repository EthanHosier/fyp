package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.services.RefactoringBurstCoordinator
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManagerListener
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

class RefactoringTemplateListener(private val project: Project) : TemplateManagerListener {

    override fun templateStarted(state: TemplateState) {
        val coordinator = project.service<RefactoringBurstCoordinator>()
        if (!coordinator.isActive()) return
        state.addTemplateStateListener(object : TemplateEditingAdapter() {
            override fun templateFinished(template: Template, brokenOff: Boolean) {
                coordinator.endRefactoringNow(
                    refactoringId = null,
                    outcome = if (brokenOff) "cancelled" else "committed",
                )
            }
        })
    }
}
