package com.github.ethanhosier.analysis.alternative.validate

import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

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
 * ## Single worktree, single batch session
 *
 * The whole [validate] call holds **one** worktree borrowed from
 * [pool] and runs **one** [runInSession] block spanning every
 * bracket. Between brackets the worktree is moved to the next
 * bracket's `fromSha` via `git reset --hard` + [refreshProject];
 * the bundle's cached `IJavaProject` (`projectRoot` keyed) stays
 * alive across all brackets because the worktree path doesn't
 * change. Net: one full project init + N cheap incremental
 * indexes, instead of N full inits.
 *
 * Per-bracket flow:
 *
 *  1. Any spec in the bracket is `null` or [RefactoringSpec.Other]
 *     → entire bracket emits [Status.UNTYPED]. We can't model the
 *     unknown op.
 *  2. Compute the user-changed `.java` set
 *     (`git diff --name-status -M fromSha toSha -- '*.java'`).
 *  3. Hash the user's `toSha` ASTs for those paths via
 *     [JavaFileAstHasher.hashFileAtSha] (cached) — no second
 *     worktree borrow.
 *  4. Apply every spec in stepIndex order on the loaned worktree.
 *     First failure short-circuits the whole bracket to
 *     [Status.REFACTOR_FAILED].
 *  5. Compute our-changed `.java` set from the dirty worktree.
 *     Empty → [Status.REFACTOR_FAILED]; differs from user-set →
 *     [Status.AST_DIVERGED] (file-set mismatch).
 *  6. Hash post-apply ASTs and compare per file. All match → all
 *     steps emit [Status.VALID]; else all emit [Status.AST_DIVERGED]
 *     (file-content mismatch + the divergent paths).
 *
 * Single-step brackets are the N=1 specialisation of the above.
 */
class RefactoringStepValidator(
    private val applySpec: (RefactoringSpec, Path) -> SpecDispatcher.Result,
    private val pool: WorktreePool,
    private val shadowGit: GitRunner,
    /** When non-null, divergent brackets write their post-apply
     *  files (`.ours`) and the user's `toSha` files (`.user`) under
     *  this directory. Singleton brackets dump under
     *  `step-<stepIndex>/`; multi-step brackets dump under
     *  `bracket-<minStepIndex>/`. */
    private val debugDumpDir: Path? = null,
    /** Run [body] inside a bundle batch-session so consecutive
     *  [applySpec] calls share one project init+index. Default:
     *  identity (no batching) — fine for tests that fake out
     *  [applySpec] and don't need a real session. */
    private val runInSession: (() -> Unit) -> Unit = { it() },
    /** Force the bundle's cached project to re-stat on-disk files.
     *  Called between brackets after `git reset --hard` so Eclipse's
     *  resource model picks up the new state. Default: no-op for
     *  tests with no real bundle behind them. */
    private val refreshProject: () -> Unit = { /* no-op */ },
) {

    /** Wires the production seams: real [SpecDispatcher] + the
     *  bundle's `withBatchSession` and `refreshProject`. Tests use
     *  the primary constructor with a fake [applySpec] lambda. */
    constructor(
        client: RefactoringClient,
        pool: WorktreePool,
        shadowGit: GitRunner,
        dispatcher: SpecDispatcher = SpecDispatcher(client),
        debugDumpDir: Path? = null,
    ) : this(
        applySpec = dispatcher::apply,
        pool = pool,
        shadowGit = shadowGit,
        debugDumpDir = debugDumpDir,
        runInSession = { body -> client.withBatchSession(body) },
        refreshProject = { client.refreshProject() },
    )

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

        // Preflight: classify untyped / empty-diff brackets without
        // touching the bundle or borrowing a worktree. Only brackets
        // that need a real apply make it into [toReplay].
        val results = mutableListOf<StepValidation>()
        data class ReplayJob(val group: List<RefactoringStep>, val userChanged: Set<String>)
        val toReplay = mutableListOf<ReplayJob>()
        for (group in brackets) {
            when (val pre = preflight(group)) {
                is Preflight.Resolved -> results += pre.verdict
                is Preflight.Replay -> toReplay += ReplayJob(group, pre.userChanged)
            }
        }
        if (toReplay.isEmpty()) return results.sortedBy { it.stepIndex }

        val firstFromSha = toReplay.first().group.first().fromSha
        val worktree = pool.borrow(firstFromSha)
        val worktreeGit = GitRunner(worktree)
        try {
            // One session covers every replay-needing bracket so the
            // bundle keeps its cached IJavaProject alive across the
            // whole call. Between brackets we git-reset to the next
            // fromSha and refreshProject so Eclipse re-stats disk;
            // bundle's projectRoot is unchanged and the cache
            // survives.
            runInSession {
                for ((idx, job) in toReplay.withIndex()) {
                    if (idx > 0) {
                        // Clobber the previous bracket's dirty state
                        // (a successful apply leaves user-changes on
                        // disk) and move HEAD to this bracket's
                        // fromSha. resetHard works whether the prev
                        // bracket succeeded or failed.
                        worktreeGit.resetHard(job.group.first().fromSha)
                        runCatching { refreshProject() }
                    }
                    results += replayBracket(job.group, job.userChanged, worktree, worktreeGit)
                }
            }
        } finally {
            pool.release(worktree)
        }
        return results.sortedBy { it.stepIndex }
    }

    private sealed interface Preflight {
        data class Resolved(val verdict: List<StepValidation>) : Preflight
        data class Replay(val userChanged: Set<String>) : Preflight
    }

    private fun preflight(group: List<RefactoringStep>): Preflight {
        require(group.isNotEmpty()) { "empty bracket" }
        val first = group.first()

        // Untyped specs short-circuit the whole bracket.
        val untypedStep = group.firstOrNull { it.spec == null || it.spec is RefactoringSpec.Other }
        if (untypedStep != null) {
            val reason = if (untypedStep.spec == null) {
                "no typed RefactoringSpec at step #${untypedStep.stepIndex}"
            } else {
                "RefactoringSpec.Other at step #${untypedStep.stepIndex}"
            }
            return Preflight.Resolved(
                group.map { StepValidation(it.stepIndex, Status.UNTYPED, reason, divergedFiles = null) },
            )
        }

        val userChanged = changedFilesBetweenCached(first.fromSha, first.toSha)
        if (userChanged.isEmpty()) {
            return Preflight.Resolved(
                group.allWith(Status.AST_DIVERGED, "user touched no .java files between fromSha and toSha"),
            )
        }
        return Preflight.Replay(userChanged)
    }

    private fun replayBracket(
        group: List<RefactoringStep>,
        userChanged: Set<String>,
        worktree: Path,
        worktreeGit: GitRunner,
    ): List<StepValidation> {
        val first = group.first()
        val toSha = first.toSha

        // Pre-fetch toSha hashes (cached). Reads from the shadow
        // repo via `git show` — no second worktree borrow needed.
        val userHashes = hashesAtSha(toSha, userChanged)

        val dumpDirName = if (group.size == 1) "step-${first.stepIndex}" else "bracket-${first.stepIndex}"

        // Apply every spec in stepIndex order. First failure
        // short-circuits the whole bracket. The miner is responsible
        // for ordering steps within a bracket so host-method
        // dependencies (e.g. ExtractVariable inside a method that a
        // sibling InlineMethod will delete) are already respected.
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
        // Hash our post-apply files on the worktree we already hold.
        val ourHashes = userChanged.associateWith { JavaFileAstHasher.hashFile(worktree, it) }
        // Snapshot post-apply files speculatively — we may discard
        // them if the comparison turns out clean.
        if (debugDumpDir != null) {
            stashOurDump(dumpDirName, worktree, userChanged)
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

    /** Hashes [paths] at [sha] via `git show`, reusing cached
     *  entries. No worktree borrow — works while the validator
     *  holds its single long-lived worktree. */
    private fun hashesAtSha(sha: String, paths: Set<String>): Map<String, String?> {
        val out = HashMap<String, String?>(paths.size)
        for (p in paths) {
            val cached = toShaHashCache[sha to p]
            if (cached != null || toShaHashCache.containsKey(sha to p)) {
                out[p] = cached
                continue
            }
            val h = JavaFileAstHasher.hashFileAtSha(shadowGit, sha, p)
            out[p] = h
            if (toShaHashCache.size < cacheCap) toShaHashCache.putIfAbsent(sha to p, h)
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
        for (rel in paths) {
            val src = shadowGit.showAtSha(toSha, rel) ?: continue
            val dst = root.resolve("$rel.user")
            Files.createDirectories(dst.parent)
            Files.writeString(dst, src)
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
