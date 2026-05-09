package com.github.ethanhosier.refactoringbundle.internal

import org.eclipse.core.resources.IResource
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
 *
 * ## Project caching for batch applies
 *
 * When [keepProjectFlag] is set (via [setKeepProject]), [run] keeps
 * the initialised [IJavaProject] alive across consecutive calls on
 * the *same* `projectRoot`. The first call still pays a full
 * init+index; subsequent calls skip both. On a different `projectRoot`
 * the cached project is torn down and replaced. [clearProjectCache]
 * tears down whatever's cached.
 *
 * Cache lifetime is bounded by the analysis-side `withBatchSession`
 * helper (which holds [com.github.ethanhosier.analysis.refactoring.RefactoringClient]'s
 * `ReentrantLock` for the duration of the batch and always calls
 * [clearProjectCache] in a `finally`). No state ever survives past
 * a batch; single-spec callers never see the cache.
 */
internal object RefactoringHost {

    @Volatile
    private var cachedProject: IJavaProject? = null

    @Volatile
    private var cachedProjectRoot: String? = null

    @Volatile
    private var keepProjectFlag: Boolean = false

    /**
     * Test-only counter incremented every time [ProjectInitializer.initProject]
     * is invoked from within [run]. Lets bundle smoke tests verify that a
     * batch of N applies inits the project exactly once. Not part of the
     * production wire format.
     */
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

    /**
     * Force the cached project to re-stat every file, propagating
     * any disk-side changes (e.g. an out-of-band `git checkout`)
     * into Eclipse's `IResource` model. Standard pathway for
     * "files modified by something other than JDT" — IDE users
     * exercise it constantly. No-op if no project is cached.
     *
     * Cost is proportional to file count. Don't call on hot paths
     * inside a single refactoring; it's intended to bracket a
     * wholesale on-disk state change like a git operation.
     */
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
                // Even on a hit, wait for the index to settle so that any
                // edits the previous step wrote via JDT have been picked
                // up by the incremental indexer. Cheap when nothing has
                // changed; cheap-ish after small edits.
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
                // The just-deleted project might *also* be the cached
                // one if the flag was flipped off mid-session. Belt and
                // braces: if so, drop the references too.
                if (cachedProject === javaProject) {
                    cachedProject = null
                    cachedProjectRoot = null
                }
            }
            // keep=true: project stays alive in the cache. Teardown
            // happens via [clearProjectCache] (called in the analysis-
            // side `withBatchSession` finally block).
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
