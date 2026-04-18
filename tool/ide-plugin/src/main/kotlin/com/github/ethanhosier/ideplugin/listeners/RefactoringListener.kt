package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.services.RefactoringBurstCoordinator
import com.github.ethanhosier.ideplugin.util.shouldCapture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener

/**
 * Bridges IntelliJ's RefactoringEventListener topic into [RefactoringBurstCoordinator],
 * which owns the `in-refactoring` flag, collects changed file contents during the
 * refactoring, and emits REFACTORING_STARTED / REFACTORING_FINISHED.
 *
 * Modal refactorings (Rename dialog, Move dialog, Change Signature, etc.) reliably
 * produce one of `refactoringDone` / `conflictsDetected` / `undoRefactoring` as a
 * terminator. In-place / preview refactorings do not — they are terminated via
 * [RefactoringTemplateListener] instead. The coordinator handles either source
 * idempotently.
 */
class RefactoringListener(private val project: Project) : RefactoringEventListener {

    override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {
        project.service<RefactoringBurstCoordinator>().beginRefactoring(refactoringId)
    }

    override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
        val extras = collectAfterDataSnapshots(afterData)
        project.service<RefactoringBurstCoordinator>()
            .endRefactoring(refactoringId, outcome = "done", extraSnapshots = extras)
    }

    private fun collectAfterDataSnapshots(afterData: RefactoringEventData?): Map<String, String> {
        if (afterData == null) return emptyMap()
        return ApplicationManager.getApplication().runReadAction<Map<String, String>> {
            val out = LinkedHashMap<String, String>()
            val elements = buildList<PsiElement> {
                afterData.getUserData(RefactoringEventData.PSI_ELEMENT_KEY)?.let { add(it) }
                afterData.getUserData(RefactoringEventData.PSI_ELEMENT_ARRAY_KEY)?.forEach { add(it) }
                afterData.getUserData(RefactoringEventData.USAGE_INFOS_KEY)?.forEach { usage ->
                    usage.element?.let { add(it) }
                }
            }
            for (element in elements) {
                if (!element.isValid) continue
                val vFile: VirtualFile = element.containingFile?.virtualFile ?: continue
                if (!vFile.shouldCapture()) continue
                if (out.containsKey(vFile.path)) continue
                val contents = FileDocumentManager.getInstance().getDocument(vFile)?.text
                    ?: runCatching { String(vFile.contentsToByteArray()) }.getOrNull()
                    ?: continue
                out[vFile.path] = contents
            }
            out
        }
    }

    override fun conflictsDetected(refactoringId: String, conflictsData: RefactoringEventData) {
        project.service<RefactoringBurstCoordinator>().endRefactoring(refactoringId, outcome = "conflicts")
    }

    override fun undoRefactoring(refactoringId: String) {
        project.service<RefactoringBurstCoordinator>().endRefactoring(refactoringId, outcome = "undone")
    }
}
