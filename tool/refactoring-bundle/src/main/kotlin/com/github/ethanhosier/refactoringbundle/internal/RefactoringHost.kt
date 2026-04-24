package com.github.ethanhosier.refactoringbundle.internal

import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.IJavaProject
import java.nio.file.Path
import java.util.UUID

/**
 * Shared wrapper for every bundle-side refactoring op. Initialises a
 * fresh Eclipse project pointed at the caller's worktree, waits for
 * indexing, hands [body] the project, then tears the project metadata
 * down regardless of outcome. Exceptions and typed failures are both
 * serialised to JSON for the host.
 */
internal object RefactoringHost {

    fun run(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        body: (IJavaProject) -> RefactoringRunner.Outcome,
    ): String {
        val name = "rc-" + UUID.randomUUID().toString().replace("-", "")
        val javaProject: IJavaProject = try {
            val jp = ProjectInitializer.initProject(
                name = name,
                root = Path.of(projectRoot),
                sourceFolders = sourceFolders.toList(),
                classpathJars = classpathJars.map(Path::of),
            )
            IndexingGate.waitForIndex(jp)
            jp
        } catch (t: Throwable) {
            return OutcomeJson.failed("project init failed: ${t::class.simpleName}: ${t.message}")
        }

        return try {
            when (val outcome = body(javaProject)) {
                is RefactoringRunner.Outcome.Success -> OutcomeJson.ok(outcome.changedFiles)
                is RefactoringRunner.Outcome.Failure -> OutcomeJson.failed(outcome.reason)
            }
        } catch (t: Throwable) {
            val cause = generateSequence<Throwable>(t) { it.cause }.last()
            OutcomeJson.failed("${t::class.simpleName}: ${t.message ?: "<no message>"} | root=${cause::class.simpleName}: ${cause.message}")
        } finally {
            // deleteContent=false — preserves the caller's worktree
            // files; only removes Eclipse's `.project` metadata.
            runCatching {
                javaProject.project.delete(false, true, NullProgressMonitor())
            }
        }
    }
}
