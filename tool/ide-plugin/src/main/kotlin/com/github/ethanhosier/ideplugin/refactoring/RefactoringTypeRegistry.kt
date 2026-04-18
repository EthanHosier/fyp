package com.github.ethanhosier.ideplugin.refactoring

import com.github.ethanhosier.ideplugin.model.FileChangeType
import com.github.ethanhosier.ideplugin.model.FileSnapshot
import com.github.ethanhosier.ideplugin.util.shouldCapture
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.refactoring.listeners.RefactoringEventData

/**
 * Turns an IntelliJ refactoring's `(beforeData, afterData)` pair into the
 * list of [FileSnapshot]s that describe what actually happened to files on
 * disk. The semantics of the user-data keys vary per refactoring, so we
 * dispatch by `refactoringId`; unknown refactorings fall through to
 * [GenericDiffHandler] which conservatively marks everything MODIFIED.
 *
 * All handlers assume they are invoked under a read action.
 */
fun interface RefactoringTypeHandler {
    fun snapshotsFor(
        project: Project,
        beforeData: RefactoringEventData?,
        afterData: RefactoringEventData?,
    ): List<FileSnapshot>
}

// Fallback for refactorings with no registered handler. Everything the
// platform mentions in beforeData / afterData becomes MODIFIED — we refuse
// to infer CREATED/DELETED here because USAGE_INFOS_KEY entries and
// cross-file moves would trip the inference (usages pre-exist; a moved-to
// target class file pre-exists). Specific handlers opt in to CREATED /
// RENAMED / MOVED / DELETED where the semantics are known.
private val GenericDiffHandler = RefactoringTypeHandler { _, before, after ->
    val out = LinkedHashMap<String, FileSnapshot>()
    for ((path, vf) in filesFrom(after)) {
        val contents = readContents(vf) ?: continue
        out[path] = FileSnapshot(path, contents, FileChangeType.MODIFIED)
    }
    for ((path, vf) in filesFrom(before)) {
        if (out.containsKey(path)) continue
        val contents = readContents(vf) ?: continue
        out[path] = FileSnapshot(path, contents, FileChangeType.MODIFIED)
    }
    out.values.toList()
}

// For extractSuper / extractInterface / extract.class, afterData.PSI_ELEMENT_KEY
// authoritatively points at the newly-created class and beforeData.PSI_ELEMENT_KEY
// at the source. Label the new file CREATED; everything else (usage sites etc.)
// stays MODIFIED.
private val ExtractClassLikeHandler = RefactoringTypeHandler { _, before, after ->
    val beforePrimaryPath = primaryFile(before)?.path
    val afterPrimary = primaryFile(after)
    val out = LinkedHashMap<String, FileSnapshot>()
    if (afterPrimary != null && afterPrimary.path != beforePrimaryPath) {
        val contents = readContents(afterPrimary)
        if (contents != null) {
            out[afterPrimary.path] = FileSnapshot(afterPrimary.path, contents, FileChangeType.CREATED)
        }
    }
    for ((path, vf) in filesFrom(after)) {
        if (out.containsKey(path)) continue
        val contents = readContents(vf) ?: continue
        out[path] = FileSnapshot(path, contents, FileChangeType.MODIFIED)
    }
    for ((path, vf) in filesFrom(before)) {
        if (out.containsKey(path)) continue
        val contents = readContents(vf) ?: continue
        out[path] = FileSnapshot(path, contents, FileChangeType.MODIFIED)
    }
    out.values.toList()
}

private val RenameHandler = RefactoringTypeHandler { _, before, after ->
    renameOrMove(before, after, FileChangeType.RENAMED)
}

private val MoveHandler = RefactoringTypeHandler { _, before, after ->
    renameOrMove(before, after, FileChangeType.MOVED)
}

private val InlineClassHandler = RefactoringTypeHandler { _, before, after ->
    val out = LinkedHashMap<String, FileSnapshot>()
    val inlined = primaryFile(before)
    if (inlined != null) {
        out[inlined.path] = FileSnapshot(inlined.path, contents = null, changeType = FileChangeType.DELETED)
    }
    for ((path, vf) in filesFrom(after)) {
        if (out.containsKey(path)) continue
        val contents = readContents(vf) ?: continue
        out[path] = FileSnapshot(path, contents, FileChangeType.MODIFIED)
    }
    out.values.toList()
}

object RefactoringTypeRegistry {
    private val handlers: Map<String, RefactoringTypeHandler> = mapOf(
        "refactoring.extractSuper" to ExtractClassLikeHandler,
        "refactoring.extractInterface" to ExtractClassLikeHandler,
        "refactoring.extract.class" to ExtractClassLikeHandler,
        "refactoring.rename" to RenameHandler,
        "refactoring.move" to MoveHandler,
        "refactoring.inline.class" to InlineClassHandler,
    )

    fun handlerFor(refactoringId: String): RefactoringTypeHandler =
        handlers[refactoringId] ?: GenericDiffHandler
}

private fun renameOrMove(
    before: RefactoringEventData?,
    after: RefactoringEventData?,
    structuralType: FileChangeType,
): List<FileSnapshot> {
    val beforeFile = primaryFile(before)
    val afterFile = primaryFile(after)
    val out = LinkedHashMap<String, FileSnapshot>()
    if (beforeFile != null && afterFile != null && beforeFile.path != afterFile.path) {
        val contents = readContents(afterFile)
        if (contents != null) {
            out[afterFile.path] = FileSnapshot(
                path = afterFile.path,
                contents = contents,
                changeType = structuralType,
                previousPath = beforeFile.path,
            )
        }
    } else if (afterFile != null) {
        // Non-file rename (method / variable) — same file, contents edited.
        val contents = readContents(afterFile)
        if (contents != null) {
            out[afterFile.path] = FileSnapshot(afterFile.path, contents, FileChangeType.MODIFIED)
        }
    }
    for ((path, vf) in filesFrom(after)) {
        if (out.containsKey(path)) continue
        val contents = readContents(vf) ?: continue
        out[path] = FileSnapshot(path, contents, FileChangeType.MODIFIED)
    }
    return out.values.toList()
}

private fun filesFrom(data: RefactoringEventData?): LinkedHashMap<String, VirtualFile> {
    val out = LinkedHashMap<String, VirtualFile>()
    if (data == null) return out
    val elements = buildList<PsiElement> {
        data.getUserData(RefactoringEventData.PSI_ELEMENT_KEY)?.let { add(it) }
        data.getUserData(RefactoringEventData.PSI_ELEMENT_ARRAY_KEY)?.forEach { add(it) }
        data.getUserData(RefactoringEventData.USAGE_INFOS_KEY)?.forEach { usage ->
            usage.element?.let { add(it) }
        }
    }
    for (element in elements) {
        if (!element.isValid) continue
        val vFile = element.containingFile?.virtualFile ?: continue
        if (!vFile.shouldCapture()) continue
        out.putIfAbsent(vFile.path, vFile)
    }
    return out
}

private fun primaryFile(data: RefactoringEventData?): VirtualFile? {
    val element = data?.getUserData(RefactoringEventData.PSI_ELEMENT_KEY) ?: return null
    if (!element.isValid) return null
    val vFile = element.containingFile?.virtualFile ?: return null
    return vFile.takeIf { it.shouldCapture() }
}

private fun readContents(vFile: VirtualFile): String? =
    FileDocumentManager.getInstance().getDocument(vFile)?.text
        ?: runCatching { String(vFile.contentsToByteArray()) }.getOrNull()
