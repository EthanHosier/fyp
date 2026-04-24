package com.github.ethanhosier.refactoringbundle.internal

import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.ltk.core.refactoring.Change
import org.eclipse.ltk.core.refactoring.CompositeChange
import org.eclipse.ltk.core.refactoring.Refactoring
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import java.nio.file.Path

/**
 * Runs the LTK pipeline for a [Refactoring]:
 *   checkInitialConditions → checkFinalConditions → createChange → perform
 *
 * Returns the set of on-disk files the change touched, or a failure
 * reason string on any non-OK condition status. Never throws for
 * user-attributable failures (e.g. selection didn't cover clean
 * statements) — those come back as `Outcome.Failure`.
 */
internal object RefactoringRunner {

    sealed interface Outcome {
        data class Success(val changedFiles: List<Path>) : Outcome
        data class Failure(val reason: String) : Outcome
    }

    fun run(refactoring: Refactoring): Outcome {
        val pm = NullProgressMonitor()
        val initial = refactoring.checkInitialConditions(pm)
        if (initial.hasFatalError()) return Outcome.Failure(formatStatus("initial conditions", initial))
        val final = refactoring.checkFinalConditions(pm)
        if (final.hasFatalError()) return Outcome.Failure(formatStatus("final conditions", final))

        val change = refactoring.createChange(pm)
            ?: return Outcome.Failure("refactoring produced no change object")

        val affected = mutableListOf<Path>()
        collectAffected(change, affected)
        change.perform(pm)
        return Outcome.Success(affected.distinct())
    }

    private fun collectAffected(change: Change, out: MutableList<Path>) {
        if (change is CompositeChange) {
            for (child in change.children) collectAffected(child, out)
            return
        }
        pathOf(change.modifiedElement)?.let(out::add)
    }

    private fun pathOf(modified: Any?): Path? = when (modified) {
        is IFile -> modified.location?.toOSString()?.let(Path::of)
        is ICompilationUnit -> {
            val res = modified.resource
            if (res is IFile) res.location?.toOSString()?.let(Path::of) else null
        }
        else -> null
    }

    private fun formatStatus(phase: String, status: RefactoringStatus): String {
        val entries = status.entries.joinToString("; ") { it.message }
        return "$phase failed: $entries"
    }
}
