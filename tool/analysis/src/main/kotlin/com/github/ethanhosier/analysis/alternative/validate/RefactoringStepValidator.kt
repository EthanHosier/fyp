package com.github.ethanhosier.analysis.alternative.validate

import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Bracket-level ground-truth check.
 *
 * RM may surface several detected refactorings under the **same**
 * `(fromSha, toSha)` pair — i.e. the user did multiple ops in one
 * commit-bracket. The validator groups all such steps and applies
 * them **together** (in stepIndex order) on a single fromSha
 * worktree before comparing the result to the user's `toSha`. Every
 * step in the bracket inherits the bracket-level verdict; this is
 * the right semantic for the reorder enumerator (a step is safe to
 * include in a window iff its bracket replays cleanly).
 *
 * Per-bracket flow (each bracket runs as a single unit; brackets are
 * parallelised over [pool]; JDT serialisation handled by the
 * bundle's process-wide lock inside [SpecDispatcher.apply]):
 *
 *  1. Any spec in the bracket is `null` or [RefactoringSpec.Other]
 *     → entire bracket emits [Status.UNTYPED]. We can't model the
 *     unknown op.
 *  2. Compute the user-changed `.java` set
 *     (`git diff --name-status -M fromSha toSha -- '*.java'`).
 *  3. Hash the user's `toSha` ASTs for those paths (cached).
 *  4. Borrow a worktree at `fromSha`. For each step in the bracket,
 *     in stepIndex order, run [SpecDispatcher.apply]. First failure
 *     short-circuits the whole bracket to [Status.REFACTOR_FAILED].
 *  5. Compute our-changed `.java` set from the dirty worktree.
 *     Empty → [Status.REFACTOR_FAILED]; differs from user-set →
 *     [Status.AST_DIVERGED] (file-set mismatch).
 *  6. Hash post-apply ASTs and compare per file. All match → all
 *     steps emit [Status.VALID]; else all emit [Status.AST_DIVERGED]
 *     (file-content mismatch + the divergent paths).
 *
 * Single-step brackets are the N=1 specialisation of the above and
 * behave exactly as the per-step model did.
 */
class RefactoringStepValidator(
    private val applySpec: (RefactoringSpec, Path) -> SpecDispatcher.Result,
    private val pool: WorktreePool,
    private val shadowGit: GitRunner,
    private val parallelism: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1),
    /** When non-null, divergent brackets write their post-apply
     *  files (`.ours`) and the user's `toSha` files (`.user`) under
     *  this directory. Singleton brackets dump under
     *  `step-<stepIndex>/`; multi-step brackets dump under
     *  `bracket-<minStepIndex>/`. */
    private val debugDumpDir: Path? = null,
) {

    constructor(
        dispatcher: SpecDispatcher,
        pool: WorktreePool,
        shadowGit: GitRunner,
        parallelism: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1),
        debugDumpDir: Path? = null,
    ) : this(dispatcher::apply, pool, shadowGit, parallelism, debugDumpDir)

    /** Bounded `(toSha, relativePath) -> astHash` cache. */
    private val toShaHashCache = ConcurrentHashMap<Pair<String, String>, String?>()
    private val changedFilesCache = ConcurrentHashMap<Pair<String, String>, Set<String>>()
    private val cacheCap = 1024

    fun validate(steps: List<RefactoringStep>): List<StepValidation> {
        if (steps.isEmpty()) return emptyList()

        // Group by (fromSha, toSha). Within each group, sort by
        // stepIndex so apply order is deterministic and matches the
        // user's natural performance order.
        val brackets: List<List<RefactoringStep>> = steps
            .groupBy { it.fromSha to it.toSha }
            .values
            .map { group -> group.sortedBy { it.stepIndex } }

        // Collect bracket validations in parallel. Each bracket
        // produces a List<StepValidation> (one per step inside).
        val perBracket = arrayOfNulls<List<StepValidation>>(brackets.size)
        val executor = Executors.newFixedThreadPool(parallelism)
        try {
            val futures = brackets.mapIndexed { idx, group ->
                executor.submit<Unit> { perBracket[idx] = validateBracket(group) }
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

        // Flatten and re-sort by stepIndex so the output order
        // matches the input order (which is itself stepIndex-sorted
        // by the miner).
        return perBracket.asSequence()
            .flatMap { it!!.asSequence() }
            .sortedBy { it.stepIndex }
            .toList()
    }

    private fun validateBracket(group: List<RefactoringStep>): List<StepValidation> {
        require(group.isNotEmpty()) { "empty bracket" }
        val first = group.first()
        val fromSha = first.fromSha
        val toSha = first.toSha

        // Untyped specs short-circuit the whole bracket.
        val untypedStep = group.firstOrNull { it.spec == null || it.spec is RefactoringSpec.Other }
        if (untypedStep != null) {
            val reason = if (untypedStep.spec == null) {
                "no typed RefactoringSpec at step #${untypedStep.stepIndex}"
            } else {
                "RefactoringSpec.Other at step #${untypedStep.stepIndex}"
            }
            return group.map {
                StepValidation(it.stepIndex, Status.UNTYPED, reason, divergedFiles = null)
            }
        }

        val userChanged = changedFilesBetweenCached(fromSha, toSha)
        if (userChanged.isEmpty()) {
            return group.allWith(Status.AST_DIVERGED, "user touched no .java files between fromSha and toSha")
        }

        // Pre-fetch toSha hashes (cached). Done before borrowing the
        // fromSha worktree so we never hold two borrows at once.
        val userHashes = hashesAtSha(toSha, userChanged)

        val dumpDirName = if (group.size == 1) "step-${first.stepIndex}" else "bracket-${first.stepIndex}"

        val worktree = pool.borrow(fromSha)
        val ourHashes: Map<String, String?>
        try {
            // Apply every spec in stepIndex order. First failure
            // short-circuits the whole bracket.
            for (s in group) {
                val outcome = applySpec(s.spec!!, worktree)
                if (outcome is SpecDispatcher.Result.Failed) {
                    val reason = if (group.size == 1) {
                        outcome.reason
                    } else {
                        "in bracket at step #${s.stepIndex}: ${outcome.reason}"
                    }
                    return group.allWith(Status.REFACTOR_FAILED, reason)
                }
            }
            val worktreeGit = GitRunner(worktree)
            val ourChanged = worktreeGit.changedJavaFilesFromHeadDirty()
            if (ourChanged.isEmpty()) {
                val reason = if (group.size == 1) {
                    "refactoring produced no textual change"
                } else {
                    "bracket produced no textual change"
                }
                return group.allWith(Status.REFACTOR_FAILED, reason)
            }
            if (ourChanged != userChanged) {
                val onlyUser = userChanged - ourChanged
                val onlyOurs = ourChanged - userChanged
                return group.allWith(
                    Status.AST_DIVERGED,
                    "file-set mismatch (only-in-user=$onlyUser, only-in-ours=$onlyOurs)",
                    divergedFiles = (onlyUser + onlyOurs).toList(),
                )
            }
            // Hash our post-apply files while we still hold the worktree.
            ourHashes = userChanged.associateWith { JavaFileAstHasher.hashFile(worktree, it) }
            // Snapshot post-apply files speculatively — we may
            // discard them if the comparison turns out clean.
            if (debugDumpDir != null) {
                stashOurDump(dumpDirName, worktree, userChanged)
            }
        } finally {
            pool.release(worktree)
        }

        val mismatching = userChanged.filter { path ->
            val ours = ourHashes[path]
            val theirs = userHashes[path]
            ours == null || theirs == null || ours != theirs
        }
        return if (mismatching.isEmpty()) {
            if (debugDumpDir != null) {
                debugDumpDir.resolve(dumpDirName).toFile().deleteRecursively()
            }
            group.map { StepValidation(it.stepIndex, Status.VALID, reason = null, divergedFiles = null) }
        } else {
            if (debugDumpDir != null) {
                dumpUserSide(dumpDirName, toSha, mismatching)
            }
            group.allWith(Status.AST_DIVERGED, "file-content mismatch", divergedFiles = mismatching)
        }
    }

    private fun List<RefactoringStep>.allWith(
        status: Status,
        reason: String,
        divergedFiles: List<String>? = null,
    ): List<StepValidation> = map { StepValidation(it.stepIndex, status, reason, divergedFiles) }

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

    private fun stashOurDump(
        dumpDirName: String,
        worktree: Path,
        paths: Set<String>,
    ) {
        val root = debugDumpDir!!.resolve(dumpDirName)
        for (rel in paths) {
            val src = worktree.resolve(rel)
            if (!Files.exists(src)) continue
            val dst = root.resolve("$rel.ours")
            Files.createDirectories(dst.parent)
            Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun dumpUserSide(dumpDirName: String, toSha: String, paths: List<String>) {
        if (paths.isEmpty()) return
        val root = debugDumpDir!!.resolve(dumpDirName)
        val worktree = pool.borrow(toSha)
        try {
            for (rel in paths) {
                val src = worktree.resolve(rel)
                if (!Files.exists(src)) continue
                val dst = root.resolve("$rel.user")
                Files.createDirectories(dst.parent)
                Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            pool.release(worktree)
        }
    }

    data class StepValidation(
        val stepIndex: Int,
        val status: Status,
        val reason: String?,
        val divergedFiles: List<String>?,
    )

    enum class Status { VALID, REFACTOR_FAILED, AST_DIVERGED, UNTYPED }
}
