package com.github.ethanhosier.ideplugin.refactoring

import com.github.ethanhosier.ideplugin.model.FileChangeType
import com.github.ethanhosier.ideplugin.model.FileSnapshot
import com.github.ethanhosier.ideplugin.model.TouchedMember
import com.github.ethanhosier.ideplugin.util.shouldCapture
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
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
    val beforeFiles = filesFrom(before)
    for ((path, c) in filesFrom(after)) {
        val contents = readContents(c.vFile) ?: continue
        val members = TouchedMemberResolver.fromPsiElements(c.elements + (beforeFiles[path]?.elements ?: emptyList()))
        out[path] = FileSnapshot(path, contents, FileChangeType.MODIFIED, touchedMembers = members)
    }
    for ((path, c) in beforeFiles) {
        if (out.containsKey(path)) continue
        val contents = readContents(c.vFile) ?: continue
        out[path] = FileSnapshot(path, contents, FileChangeType.MODIFIED, touchedMembers = membersOf(c))
    }
    out.values.toList()
}

// For extractSuper / extractInterface / extract.class, afterData.PSI_ELEMENT_KEY
// authoritatively points at the newly-created class and beforeData.PSI_ELEMENT_KEY
// at the source. Label the new file CREATED; everything else (usage sites etc.)
// stays MODIFIED.
// Extract Superclass / Extract Interface / Extract Class don't populate
// USAGE_INFOS_KEY or PSI_ELEMENT_ARRAY_KEY with the pulled-up members — only
// the two PsiClasses end up in event data. The pulled-up set is recoverable:
// every method declared on the just-created target class IS a pulled-up
// member. We attribute those signatures to both sides:
//   - new superclass snapshot: (NewSuper, sig) for each declared method
//   - source class snapshot:   (Source, sig) for each — same sigs, other side
// This gives accurate per-method provenance without diffing beforeData.
private val ExtractClassLikeHandler = RefactoringTypeHandler { _, before, after ->
    val beforeClass = primaryPsiClass(before)
    val afterClass = primaryPsiClass(after)
    val afterPath = afterClass?.containingFile?.virtualFile?.path
    val beforePath = beforeClass?.containingFile?.virtualFile?.path
    val pulledSignatures = afterClass?.methods?.map(TouchedMemberResolver::signatureKey) ?: emptyList()
    val newSuperKey = afterClass?.let(TouchedMemberResolver::classKey)
    val sourceKey = beforeClass?.let(TouchedMemberResolver::classKey)

    val out = LinkedHashMap<String, FileSnapshot>()
    val afterFiles = filesFrom(after)
    val beforeFiles = filesFrom(before)

    if (afterClass != null && afterPath != null && afterPath != beforePath) {
        val contents = readContents(afterClass.containingFile.virtualFile)
        if (contents != null) {
            val members = buildList {
                add(TouchedMember(newSuperKey, null))
                pulledSignatures.forEach { add(TouchedMember(newSuperKey, it)) }
            }
            out[afterPath] = FileSnapshot(afterPath, contents, FileChangeType.CREATED, touchedMembers = members)
        }
    }
    for ((path, c) in afterFiles) {
        if (out.containsKey(path)) continue
        val contents = readContents(c.vFile) ?: continue
        val base = TouchedMemberResolver.fromPsiElements(c.elements + (beforeFiles[path]?.elements ?: emptyList()))
        val members = if (path == beforePath && sourceKey != null) {
            (base + pulledSignatures.map { TouchedMember(sourceKey, it) }).distinct()
        } else base
        out[path] = FileSnapshot(path, contents, FileChangeType.MODIFIED, touchedMembers = members)
    }
    for ((path, c) in beforeFiles) {
        if (out.containsKey(path)) continue
        val contents = readContents(c.vFile) ?: continue
        val base = membersOf(c)
        val members = if (path == beforePath && sourceKey != null) {
            (base + pulledSignatures.map { TouchedMember(sourceKey, it) }).distinct()
        } else base
        out[path] = FileSnapshot(path, contents, FileChangeType.MODIFIED, touchedMembers = members)
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
    val beforeFiles = filesFrom(before)
    if (inlined != null) {
        out[inlined.path] = FileSnapshot(
            inlined.path,
            contents = null,
            changeType = FileChangeType.DELETED,
            touchedMembers = membersOf(beforeFiles[inlined.path]),
        )
    }
    for ((path, c) in filesFrom(after)) {
        if (out.containsKey(path)) continue
        val contents = readContents(c.vFile) ?: continue
        val members = TouchedMemberResolver.fromPsiElements(c.elements + (beforeFiles[path]?.elements ?: emptyList()))
        out[path] = FileSnapshot(path, contents, FileChangeType.MODIFIED, touchedMembers = members)
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
    val afterFiles = filesFrom(after)
    val beforeFiles = filesFrom(before)
    if (beforeFile != null && afterFile != null && beforeFile.path != afterFile.path) {
        val contents = readContents(afterFile)
        if (contents != null) {
            out[afterFile.path] = FileSnapshot(
                path = afterFile.path,
                contents = contents,
                changeType = structuralType,
                previousPath = beforeFile.path,
                touchedMembers = membersOf(afterFiles[afterFile.path]),
            )
        }
    } else if (afterFile != null) {
        // Non-file rename (method / variable) — same file, contents edited.
        val contents = readContents(afterFile)
        if (contents != null) {
            val members = TouchedMemberResolver.fromPsiElements(
                (afterFiles[afterFile.path]?.elements ?: emptyList()) +
                    (beforeFiles[afterFile.path]?.elements ?: emptyList())
            )
            out[afterFile.path] = FileSnapshot(afterFile.path, contents, FileChangeType.MODIFIED, touchedMembers = members)
        }
    }
    for ((path, c) in afterFiles) {
        if (out.containsKey(path)) continue
        val contents = readContents(c.vFile) ?: continue
        val members = TouchedMemberResolver.fromPsiElements(c.elements + (beforeFiles[path]?.elements ?: emptyList()))
        out[path] = FileSnapshot(path, contents, FileChangeType.MODIFIED, touchedMembers = members)
    }
    return out.values.toList()
}

/**
 * Per-file accumulator: the VirtualFile plus every PsiElement the refactoring
 * keys pointed at inside it. Handlers feed `elements` into [TouchedMemberResolver]
 * to derive `touchedMembers` for the emitted [FileSnapshot].
 */
data class FileContributors(val vFile: VirtualFile, val elements: MutableList<PsiElement> = mutableListOf())

private fun filesFrom(data: RefactoringEventData?): LinkedHashMap<String, FileContributors> {
    val out = LinkedHashMap<String, FileContributors>()
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
        out.getOrPut(vFile.path) { FileContributors(vFile) }.elements.add(element)
    }
    return out
}

private fun membersOf(contributors: FileContributors?): List<TouchedMember> =
    if (contributors == null) emptyList() else TouchedMemberResolver.fromPsiElements(contributors.elements)

private fun primaryPsiClass(data: RefactoringEventData?): PsiClass? {
    val element = data?.getUserData(RefactoringEventData.PSI_ELEMENT_KEY) ?: return null
    if (!element.isValid) return null
    return element as? PsiClass
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
