package com.github.ethanhosier.analysis.alternative.validate

import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class RefactoringStepValidator(
    private val applySpec: (RefactoringSpec, Path) -> SpecDispatcher.Result,
    private val pool: WorktreePool,
    private val shadowGit: GitRunner,
    private val debugDumpDir: Path? = null,
    private val runInSession: (() -> Unit) -> Unit = { it() },
    private val refreshProject: () -> Unit = { /* no-op */ },
) {

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

    private val toShaHashCache = ConcurrentHashMap<Pair<String, String>, String?>()
    private val changedFilesCache = ConcurrentHashMap<Pair<String, String>, Set<String>>()
    private val cacheCap = 1024

    fun validate(steps: List<RefactoringStep>): List<StepValidation> {
        if (steps.isEmpty()) return emptyList()

        val brackets: List<List<RefactoringStep>> = steps
            .groupBy { it.fromSha to it.toSha }
            .values
            .map { group -> group.sortedBy { it.stepIndex } }

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
            runInSession {
                for ((idx, job) in toReplay.withIndex()) {
                    if (idx > 0) {
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
