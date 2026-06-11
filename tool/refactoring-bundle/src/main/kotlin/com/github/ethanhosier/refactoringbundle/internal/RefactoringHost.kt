package com.github.ethanhosier.refactoringbundle.internal

import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.IJavaProject
import java.nio.file.Path
import java.util.UUID

internal object RefactoringHost {

    @Volatile
    private var cachedProject: IJavaProject? = null

    @Volatile
    private var cachedProjectRoot: String? = null

    @Volatile
    private var keepProjectFlag: Boolean = false

    @Volatile
    var initCount: Int = 0
        private set

    fun setKeepProject(keep: Boolean): String {
        keepProjectFlag = keep
        return OutcomeJson.ok(emptyList())
    }

    fun clearProjectCache(): String {
        return try {
            tearDownCached()
            OutcomeJson.ok(emptyList())
        } catch (t: Throwable) {
            OutcomeJson.failed("clearProjectCache: ${t::class.simpleName}: ${t.message ?: ""}")
        }
    }

    fun refreshProject(): String {
        val jp = cachedProject ?: return OutcomeJson.ok(emptyList())
        return try {
            jp.project.refreshLocal(IResource.DEPTH_INFINITE, NullProgressMonitor())
            OutcomeJson.ok(emptyList())
        } catch (t: Throwable) {
            OutcomeJson.failed("refreshProject: ${t::class.simpleName}: ${t.message ?: ""}")
        }
    }

    fun run(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        body: (IJavaProject) -> RefactoringRunner.Outcome,
    ): String {
        val keep = keepProjectFlag
        val cacheHit = keep && cachedProjectRoot == projectRoot && cachedProject != null

        val javaProject: IJavaProject = try {
            if (cacheHit) {
                val jp = cachedProject!!
                IndexingGate.waitForIndex(jp)
                jp
            } else {
                // Different root (or no cache, or flag off). Tear down any
                // stale cache before standing a new project up.
                tearDownCached()
                val name = "rc-" + UUID.randomUUID().toString().replace("-", "")
                val jp = ProjectInitializer.initProject(
                    name = name,
                    root = Path.of(projectRoot),
                    sourceFolders = sourceFolders.toList(),
                    classpathJars = classpathJars.map(Path::of),
                )
                initCount++
                IndexingGate.waitForIndex(jp)
                if (keep) {
                    cachedProject = jp
                    cachedProjectRoot = projectRoot
                }
                jp
            }
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
            if (!keep) {
                // deleteContent=false — preserves the caller's worktree
                // files; only removes Eclipse's `.project` metadata.
                runCatching {
                    javaProject.project.delete(false, true, NullProgressMonitor())
                }
                if (cachedProject === javaProject) {
                    cachedProject = null
                    cachedProjectRoot = null
                }
            }
        }
    }

    private fun tearDownCached() {
        val jp = cachedProject ?: return
        cachedProject = null
        cachedProjectRoot = null
        runCatching {
            jp.project.delete(false, true, NullProgressMonitor())
        }
    }
}
