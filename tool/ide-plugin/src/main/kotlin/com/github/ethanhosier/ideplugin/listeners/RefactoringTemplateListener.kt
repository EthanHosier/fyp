package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.services.RefactoringBurstCoordinator
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManagerListener
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Terminator for in-place refactorings (Extract Method preview, in-place Rename, etc.).
 *
 * The platform's RefactoringEventListener fires `refactoringStarted` when the
 * preview template opens but never fires `refactoringDone` / `undoRefactoring`
 * when the user dismisses the template with Esc. To close the gap we observe
 * [TemplateManagerListener.templateStarted] and attach a per-instance
 * [TemplateEditingAdapter] whose `templateFinished(_, brokenOff)` tells us the
 * outcome.
 *
 * We only close a refactoring if one is currently active in
 * [RefactoringBurstCoordinator]; ordinary template use (live templates like
 * `fori`, surround-with, completion) is ignored because no refactoring is
 * active when they fire.
 *
 * Edge case: if a live template somehow fires *while* an in-place refactoring
 * is active, we'll close the refactoring on the inner template's finish. In
 * practice IntelliJ does not nest templates this way.
 */
class RefactoringTemplateListener(private val project: Project) : TemplateManagerListener {

    override fun templateStarted(state: TemplateState) {
        val coordinator = project.service<RefactoringBurstCoordinator>()
        if (!coordinator.isActive()) return
        state.addTemplateStateListener(object : TemplateEditingAdapter() {
            override fun templateFinished(template: Template, brokenOff: Boolean) {
                coordinator.endRefactoring(
                    refactoringId = null,
                    outcome = if (brokenOff) "cancelled" else "committed",
                )
            }
        })
    }
}
