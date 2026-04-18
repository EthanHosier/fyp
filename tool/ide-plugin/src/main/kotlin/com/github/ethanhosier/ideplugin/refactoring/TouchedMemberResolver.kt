package com.github.ethanhosier.ideplugin.refactoring

import com.github.ethanhosier.ideplugin.model.TouchedMember
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

/**
 * Resolves edited regions or refactoring PSI elements to the enclosing
 * `(class, method?)` pairs. Shared by EDIT_BURST (range-based) and
 * REFACTORING_FINISHED (element-based) event capture.
 *
 * All entry points must be called under a read action.
 */
object TouchedMemberResolver {

    fun fromRange(psiFile: PsiFile, start: Int, end: Int): List<TouchedMember> {
        val clampedEnd = minOf(end, psiFile.textLength)
        val clampedStart = minOf(start, clampedEnd)
        val out = LinkedHashSet<TouchedMember>()
        var cursor = clampedStart
        while (cursor <= clampedEnd) {
            val element = psiFile.findElementAt(cursor)
            if (element == null) {
                cursor++
                continue
            }
            memberFor(element)?.let(out::add)
            val next = element.textRange.endOffset
            cursor = if (next > cursor) next else cursor + 1
        }
        return out.toList()
    }

    fun fromPsiElements(elements: List<PsiElement>): List<TouchedMember> {
        val out = LinkedHashSet<TouchedMember>()
        for (el in elements) {
            if (!el.isValid) continue
            memberFor(el)?.let(out::add)
        }
        return out.toList()
    }

    fun classKey(cls: PsiClass): String? = cls.qualifiedName ?: cls.name

    fun signatureKey(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(", ") { it.type.presentableText }
        return "${method.name}($params)"
    }

    private fun memberFor(element: PsiElement): TouchedMember? {
        when (element) {
            is PsiMethod -> return TouchedMember(element.containingClass?.let(::classKey), signatureKey(element))
            is PsiClass -> return TouchedMember(classKey(element), null)
        }
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false)
        if (method != null) {
            return TouchedMember(method.containingClass?.let(::classKey), signatureKey(method))
        }
        val cls = PsiTreeUtil.getParentOfType(element, PsiClass::class.java, false)
        if (cls != null) {
            return TouchedMember(classKey(cls), null)
        }
        return null
    }
}
