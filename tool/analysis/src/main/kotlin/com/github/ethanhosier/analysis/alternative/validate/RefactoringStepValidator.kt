package com.github.ethanhosier.analysis.alternative.validate

import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Per-step ground-truth check: for each typed [RefactoringStep],
 * replay `step.spec` from `step.fromSha` and check whether the
 * resulting AST matches the user's `step.toSha`.
 *
 * The classification feeds windowing for the reorder enumerator —
 * only [Status.VALID] steps are eligible to participate in a
 * reorder window. Anything else (apply failed, our reapplication
 * doesn't match the user, or the step had no typed spec) acts as a
 * window splitter.
 *
 * Per-step flow (parallelised over [pool]; JDT serialisation handled
 * by the bundle's process-wide lock inside [SpecDispatcher.apply]):
 *
 *  1. `spec == null` or [RefactoringSpec.Other] → [Status.UNTYPED].
 *  2. Compute the user-changed `.java` set
 *     (`git diff --name-status -M fromSha toSha -- '*.java'`).
 *  3. Hash the user's `toSha` ASTs for those paths (cached).
 *  4. Borrow a worktree at `fromSha`. Run [SpecDispatcher.apply].
 *     Failure → [Status.REFACTOR_FAILED].
 *  5. Compute our-changed `.java` set from the dirty worktree.
 *     Empty → [Status.REFACTOR_FAILED] (no textual change).
 *     Differs from user-set → [Status.AST_DIVERGED] (file-set
 *     mismatch).
 *  6. Hash our post-apply ASTs and compare per file. All match →
 *     [Status.VALID]; else [Status.AST_DIVERGED] (file-content
 *     mismatch + the divergent paths).
 */
class RefactoringStepValidator(
    private val applySpec: (RefactoringSpec, java.nio.file.Path) -> SpecDispatcher.Result,
    private val pool: WorktreePool,
    private val shadowGit: GitRunner,
    private val parallelism: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1),
) {

    constructor(
        dispatcher: SpecDispatcher,
        pool: WorktreePool,
        shadowGit: GitRunner,
        parallelism: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1),
    ) : this(dispatcher::apply, pool, shadowGit, parallelism)

    /** Bounded `(toSha, relativePath) -> astHash` cache. Hashing a
     *  given `(toSha, path)` is deterministic, so chained traces
     *  that share a `toSha` only pay the I/O once. Capped to keep
     *  memory predictable. */
    private val toShaHashCache = ConcurrentHashMap<Pair<String, String>, String?>()
    private val changedFilesCache = ConcurrentHashMap<Pair<String, String>, Set<String>>()
    private val cacheCap = 1024

    fun validate(steps: List<RefactoringStep>): List<StepValidation> {
        if (steps.isEmpty()) return emptyList()
        val results = arrayOfNulls<StepValidation>(steps.size)
        val executor = Executors.newFixedThreadPool(parallelism)
        try {
            val futures = steps.mapIndexed { idx, step ->
                executor.submit<Unit> { results[idx] = validateOne(step) }
            }
            futures.forEach { f ->
                try {
                    f.get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(1, TimeUnit.MINUTES)
        }
        return results.map { it!! }
    }

    private fun validateOne(step: RefactoringStep): StepValidation {
        val spec = step.spec
        if (spec == null || spec is RefactoringSpec.Other) {
            return StepValidation(
                stepIndex = step.stepIndex,
                status = Status.UNTYPED,
                reason = if (spec == null) "no typed RefactoringSpec" else "RefactoringSpec.Other",
                divergedFiles = null,
            )
        }

        val userChanged = changedFilesBetweenCached(step.fromSha, step.toSha)
        if (userChanged.isEmpty()) {
            return StepValidation(
                stepIndex = step.stepIndex,
                status = Status.AST_DIVERGED,
                reason = "user touched no .java files between fromSha and toSha",
                divergedFiles = null,
            )
        }

        // Pre-fetch toSha hashes (cached). Done before borrowing the
        // fromSha worktree so we never hold two borrows at once.
        val userHashes = hashesAtSha(step.toSha, userChanged)

        val worktree = pool.borrow(step.fromSha)
        val ourHashes: Map<String, String?>
        val ourChanged: Set<String>
        try {
            val applyResult = applySpec(spec, worktree)
            if (applyResult is SpecDispatcher.Result.Failed) {
                return StepValidation(
                    stepIndex = step.stepIndex,
                    status = Status.REFACTOR_FAILED,
                    reason = applyResult.reason,
                    divergedFiles = null,
                )
            }
            val worktreeGit = GitRunner(worktree)
            ourChanged = worktreeGit.changedJavaFilesFromHeadDirty()
            if (ourChanged.isEmpty()) {
                return StepValidation(
                    stepIndex = step.stepIndex,
                    status = Status.REFACTOR_FAILED,
                    reason = "refactoring produced no textual change",
                    divergedFiles = null,
                )
            }
            if (ourChanged != userChanged) {
                val onlyUser = userChanged - ourChanged
                val onlyOurs = ourChanged - userChanged
                return StepValidation(
                    stepIndex = step.stepIndex,
                    status = Status.AST_DIVERGED,
                    reason = "file-set mismatch (only-in-user=$onlyUser, only-in-ours=$onlyOurs)",
                    divergedFiles = (onlyUser + onlyOurs).toList(),
                )
            }
            // Hash our post-apply files while we still hold the worktree.
            ourHashes = userChanged.associateWith { JavaFileAstHasher.hashFile(worktree, it) }
        } finally {
            pool.release(worktree)
        }

        val mismatching = userChanged.filter { path ->
            val ours = ourHashes[path]
            val theirs = userHashes[path]
            ours == null || theirs == null || ours != theirs
        }
        return if (mismatching.isEmpty()) {
            StepValidation(step.stepIndex, Status.VALID, reason = null, divergedFiles = null)
        } else {
            StepValidation(
                stepIndex = step.stepIndex,
                status = Status.AST_DIVERGED,
                reason = "file-content mismatch",
                divergedFiles = mismatching,
            )
        }
    }

    private fun changedFilesBetweenCached(fromSha: String, toSha: String): Set<String> {
        val key = fromSha to toSha
        changedFilesCache[key]?.let { return it }
        val computed = shadowGit.changedJavaFilesBetween(fromSha, toSha)
        if (changedFilesCache.size < cacheCap) changedFilesCache.putIfAbsent(key, computed)
        return computed
    }

    /** Hashes [paths] at [sha], reusing cached entries. Borrows a
     *  worktree at [sha] only if at least one path is uncached. */
    private fun hashesAtSha(sha: String, paths: Set<String>): Map<String, String?> {
        val out = HashMap<String, String?>(paths.size)
        val missing = mutableListOf<String>()
        for (p in paths) {
            val cached = toShaHashCache[sha to p]
            if (cached != null || toShaHashCache.containsKey(sha to p)) {
                out[p] = cached
            } else {
                missing += p
            }
        }
        if (missing.isEmpty()) return out
        val worktree = pool.borrow(sha)
        try {
            for (p in missing) {
                val h = JavaFileAstHasher.hashFile(worktree, p)
                out[p] = h
                if (toShaHashCache.size < cacheCap) toShaHashCache.putIfAbsent(sha to p, h)
            }
        } finally {
            pool.release(worktree)
        }
        return out
    }

    data class StepValidation(
        val stepIndex: Int,
        val status: Status,
        val reason: String?,
        val divergedFiles: List<String>?,
    )

    enum class Status { VALID, REFACTOR_FAILED, AST_DIVERGED, UNTYPED }
}
