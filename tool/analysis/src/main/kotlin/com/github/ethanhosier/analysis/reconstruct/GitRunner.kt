package com.github.ethanhosier.analysis.reconstruct

import java.nio.file.Path

/**
 * Thin wrapper around the system `git` binary, invoked via [ProcessBuilder].
 *
 * Chosen over JGit because JGit does not support `git worktree`, which we need
 * for later analysis stages. Assumes `git >= 2.30` on `PATH`; calls [version]
 * to verify up front.
 *
 * Every non-zero exit from a command that isn't explicitly tolerating one
 * raises a [GitException] carrying the full argv, exit code, and stderr so
 * failures surface with context instead of silent truncation.
 */
class GitRunner(private val workDir: Path) {

    fun version(): String = run("--version").trim()

    fun init() {
        run("init", "--quiet")
    }

    /**
     * Writes `user.email` / `user.name` to the repo's local config so commits
     * never depend on whatever the host's global git config happens to say.
     */
    fun setLocalIdentity(email: String, name: String) {
        run("config", "--local", "user.email", email)
        run("config", "--local", "user.name", name)
    }

    fun addAll() {
        run("add", "-A")
    }

    /**
     * `git add -A` but skips the given pathspecs. Used by the alt-trajectory
     * synthesiser to keep Eclipse JDT/LTK workspace metadata (`.project`,
     * `.classpath`, `.settings/`) out of synthesised alt SHAs — those files
     * are created by Eclipse as a side effect of running refactorings
     * headlessly and would otherwise pollute every alt-vs-baseline diff.
     */
    fun addAllExcept(vararg pathspecs: String) {
        val args = mutableListOf("add", "-A", "--", ".")
        for (p in pathspecs) args += ":(exclude)$p"
        run(*args.toTypedArray())
    }

    /** `true` iff the index holds changes relative to HEAD. */
    fun hasStagedChanges(): Boolean {
        // `git diff --cached --quiet` exits 0 when no staged diff, 1 when there is.
        // Anything else is a real error.
        val result = exec(listOf("diff", "--cached", "--quiet"), allowNonZero = true)
        return when (result.exitCode) {
            0 -> false
            1 -> true
            else -> throw GitException(
                command = listOf("git", "diff", "--cached", "--quiet"),
                exitCode = result.exitCode,
                stderr = result.stderr.trim(),
            )
        }
    }

    /** Commits the index with [message] and returns the resulting HEAD SHA. */
    fun commit(message: String): String {
        run("commit", "-m", message, "--allow-empty-message")
        return head()
    }

    fun head(): String = run("rev-parse", "HEAD").trim()

    /** Unified-diff text of staged changes (`git diff --cached`). */
    fun stagedDiff(): String = run("diff", "--cached")

    /** Discards both index and working-tree changes, returning the
     *  repo to a clean HEAD. Used to roll back a staged event whose
     *  only effect was whitespace-only churn. */
    fun resetHard() {
        run("reset", "--hard", "HEAD")
    }

    /** Resolve [ref] (a branch name, tag, or shorthand SHA) to a full
     *  40-char SHA. Errors if the ref doesn't resolve. */
    fun revParse(ref: String): String = run("rev-parse", ref).trim()

    /**
     * Adds a new linked worktree at [path], checked out in detached-HEAD state
     * at [sha]. The parent directory must exist; [path] itself must not.
     */
    fun worktreeAdd(path: Path, sha: String) {
        run("worktree", "add", "--detach", path.toAbsolutePath().toString(), sha)
    }

    /**
     * Removes the linked worktree at [path], force-removing even if the
     * working tree has untracked changes (a checkpoint run may have left
     * `build/` etc. behind).
     */
    fun worktreeRemove(path: Path) {
        run("worktree", "remove", "--force", path.toAbsolutePath().toString())
    }

    /**
     * Cleans up admin entries for worktrees whose directories no longer
     * exist. Cheap to run defensively before provisioning new worktrees, so
     * a crashed prior run doesn't wedge the next one.
     */
    fun worktreePrune() {
        run("worktree", "prune")
    }

    /**
     * Switches the working tree to [sha] in detached-HEAD state. Untracked
     * files (e.g. `build/` from a prior checkpoint) are preserved — callers
     * relying on incremental compile state want that. Tracked files that
     * would be overwritten are replaced; tracked files only present in the
     * old SHA but not the new one are removed.
     */
    fun checkoutDetach(sha: String) {
        run("checkout", "--detach", sha)
    }

    /**
     * Force the working tree, index, and HEAD to [sha]. Clobbers any
     * dirty changes — required when callers know the prior state was
     * left dirty by a refactoring apply but want to start the next
     * bracket from a clean snapshot.
     */
    fun resetHard(sha: String) {
        run("reset", "--hard", sha)
    }

    /**
     * `git reset --soft <sha>` — moves HEAD without touching worktree
     * or index. Used to squash a chain of synthesised commits into a
     * single alt-SHA: reset to the anchor with everything still staged,
     * then [commit] once.
     */
    fun resetSoft(sha: String) {
        run("reset", "--soft", sha)
    }

    /**
     * Result of `git apply --3way --index`. [Ok] means every hunk landed
     * (cleanly or via 3-way merge resolution) and the changes are
     * staged. [Conflict] means at least one hunk could not be merged;
     * the caller is responsible for [resetHard]-ing back to a clean
     * baseline, no auto-rollback here.
     */
    sealed interface ApplyResult {
        data class Ok(val added: Int, val deleted: Int) : ApplyResult
        data class Conflict(
            val rejectedFiles: List<String>,
            val added: Int,
            val deleted: Int,
            val reason: String,
        ) : ApplyResult
    }

    /**
     * `git apply --3way --index <patchFile>`. Stages successfully
     * merged hunks; conflicts surface in stderr as
     * `error: <path>: patch does not apply` lines which we pull into
     * [ApplyResult.Conflict.rejectedFiles]. Line counts come from
     * `git diff --numstat --cached` so the caller can record how much
     * landed (or how much was dropped on conflict).
     */
    fun applyThreeWay(patchFile: Path): ApplyResult {
        val result = exec(
            listOf("apply", "--3way", "--index", patchFile.toString()),
            allowNonZero = true,
        )
        val numstat = exec(listOf("diff", "--numstat", "--cached"), allowNonZero = false).stdout
        val (added, deleted) = sumNumstat(numstat)
        if (result.exitCode == 0) {
            return ApplyResult.Ok(added = added, deleted = deleted)
        }
        val rejected = parseRejectedFiles(result.stderr)
        val reason = result.stderr.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: "git apply --3way exited ${result.exitCode}"
        return ApplyResult.Conflict(
            rejectedFiles = rejected,
            added = added,
            deleted = deleted,
            reason = reason,
        )
    }

    /**
     * Plain `git apply --index --whitespace=nowarn <patchFile>` — no
     * 3-way merge fallback. Used by the rework alt-builder where the
     * caller has already line-renumbered the patch into the
     * synthetic-tree's coord system, so the literal hunk is expected
     * to apply against the index. `--3way` would consult the patch's
     * pre-blob OIDs (which point at the user's tree) for merge base
     * — exactly the divergent state surgery is meant to avoid —
     * producing spurious conflicts.
     *
     * `--whitespace=nowarn` suppresses benign trailing-whitespace
     * warnings that would otherwise clutter the stderr "reason"
     * surfaced on real failures.
     */
    fun applyDirect(patchFile: Path): ApplyResult {
        val result = exec(
            // --unidiff-zero is required: the rework alt-builder produces
            // patches with no context lines (`-U0`). Without this flag,
            // git apply falls back to fuzzy context matching and silently
            // misplaces pure-insertion hunks (typically dumping them at
            // end-of-file instead of the literal hunk position).
            listOf("apply", "--index", "--unidiff-zero", "--whitespace=nowarn", patchFile.toString()),
            allowNonZero = true,
        )
        val numstat = exec(listOf("diff", "--numstat", "--cached"), allowNonZero = false).stdout
        val (added, deleted) = sumNumstat(numstat)
        if (result.exitCode == 0) {
            return ApplyResult.Ok(added = added, deleted = deleted)
        }
        val rejected = parseRejectedFiles(result.stderr)
        val reason = result.stderr.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: "git apply exited ${result.exitCode}"
        return ApplyResult.Conflict(
            rejectedFiles = rejected,
            added = added,
            deleted = deleted,
            reason = reason,
        )
    }

    private fun sumNumstat(numstat: String): Pair<Int, Int> {
        var added = 0
        var deleted = 0
        for (raw in numstat.lineSequence()) {
            val line = raw.trimEnd('\r')
            if (line.isBlank()) continue
            val parts = line.split('\t')
            if (parts.size < 2) continue
            // Binary files come through as "-\t-\t<path>" — count as 0/0.
            added += parts[0].toIntOrNull() ?: 0
            deleted += parts[1].toIntOrNull() ?: 0
        }
        return added to deleted
    }

    private fun parseRejectedFiles(stderr: String): List<String> {
        val out = LinkedHashSet<String>()
        // git apply --3way stderr commonly contains lines like
        //   "error: <path>: patch does not apply"
        //   "error: <path>: does not match index"
        // Either way the path is the chunk between "error: " and the
        // first ":" that follows.
        for (raw in stderr.lineSequence()) {
            val line = raw.trimEnd('\r').trim()
            if (!line.startsWith("error: ")) continue
            val rest = line.removePrefix("error: ")
            val colon = rest.indexOf(':')
            if (colon <= 0) continue
            out += rest.substring(0, colon).trim()
        }
        return out.toList()
    }

    /**
     * Raw lines from `git diff --numstat -M <from> <to>`. Each line is
     * `added\tdeleted\tpath` where `added` / `deleted` are `-` for binary
     * files. Rename entries use the `{from => to}` path syntax.
     */
    fun diffNumstat(from: String, to: String): List<String> =
        run("diff", "--numstat", "-M", from, to).lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()

    /**
     * Raw lines from `git diff --name-status -M <from> <to>`. Each line is
     * `status\tpath` for A/M/D or `R###\told\tnew` / `C###\told\tnew` for
     * renames/copies (### = similarity percentage).
     */
    fun diffNameStatus(from: String, to: String): List<String> =
        run("diff", "--name-status", "-M", from, to).lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()

    /**
     * Full unified-diff patch text for `git diff -M -U<contextLines> <from> <to> [-- paths]`.
     *
     * `-M` so renames collapse (matches what RefactoringMiner sees). Pass
     * [paths] to scope the diff to a subset of files; an empty list means
     * the whole tree. Result is the raw patch, untrimmed — empty string if
     * there is no textual delta for the scope.
     */
    fun diffPatch(
        from: String,
        to: String,
        paths: List<String> = emptyList(),
        contextLines: Int = 3,
    ): String {
        val args = mutableListOf("diff", "-M", "-U$contextLines", from, to)
        if (paths.isNotEmpty()) {
            args += "--"
            args += paths
        }
        return exec(args, allowNonZero = false).stdout
    }

    /**
     * Create or move a branch named [name] to point at [sha]. Equivalent
     * to `git branch -f <name> <sha>`. Used to attach reachable refs to
     * synthesised commits so they survive across processes / GC.
     */
    fun branchForce(name: String, sha: String) {
        run("branch", "-f", name, sha)
    }

    /**
     * `.java` paths changed between [from] and [to] — adds, modifies,
     * deletes and both sides of a rename. Uses `--name-status -M` so
     * a rename surfaces as both old and new path (the validator needs
     * to compare ASTs at both ends).
     */
    fun changedJavaFilesBetween(from: String, to: String): Set<String> {
        val out = exec(
            listOf("diff", "--name-status", "-M", from, to, "--", "*.java"),
            allowNonZero = false,
        ).stdout
        val result = LinkedHashSet<String>()
        for (raw in out.lineSequence()) {
            val line = raw.trimEnd('\r')
            if (line.isBlank()) continue
            val parts = line.split('\t')
            // status\tpath  OR  R###\told\tnew  /  C###\told\tnew
            val status = parts[0]
            when {
                status.startsWith("R") || status.startsWith("C") -> {
                    if (parts.size >= 3) {
                        result += parts[1]
                        result += parts[2]
                    }
                }
                else -> if (parts.size >= 2) result += parts[1]
            }
        }
        return result
    }

    /**
     * `.java` paths changed in the worktree relative to HEAD —
     * tracked-modified, added, deleted, plus untracked files.
     * Mirrors [changedJavaFilesBetween] for a dirty working tree
     * the validator just produced.
     */
    fun changedJavaFilesFromHeadDirty(): Set<String> {
        val out = exec(
            listOf("status", "--porcelain", "--untracked-files=all", "--", "*.java"),
            allowNonZero = false,
        ).stdout
        val result = LinkedHashSet<String>()
        for (raw in out.lineSequence()) {
            val line = raw.trimEnd('\r')
            if (line.length < 4) continue
            // Porcelain v1: XY<space>path  (or XY<space>old -> new for renames).
            val rest = line.substring(3)
            val arrow = rest.indexOf(" -> ")
            if (arrow >= 0) {
                result += rest.substring(0, arrow)
                result += rest.substring(arrow + 4)
            } else {
                result += rest
            }
        }
        return result
    }

    /** First-parent SHA of [sha], or null if it's the root commit. */
    fun parentOf(sha: String): String? {
        val result = exec(listOf("rev-parse", "--verify", "$sha^"), allowNonZero = true)
        return if (result.exitCode == 0) result.stdout.trim() else null
    }

    /**
     * Read the contents of [relativePath] at [sha] without touching
     * the working tree. Returns `null` if the path does not exist at
     * that SHA (or any other git error). Useful for hashing files at
     * historical states without borrowing a worktree.
     */
    fun showAtSha(sha: String, relativePath: String): String? {
        val result = exec(listOf("show", "$sha:$relativePath"), allowNonZero = true)
        return if (result.exitCode == 0) result.stdout else null
    }

    private data class ExecResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun run(vararg args: String): String =
        exec(args.toList(), allowNonZero = false).stdout

    private fun exec(args: List<String>, allowNonZero: Boolean): ExecResult {
        val argv = listOf("git") + args
        val process = ProcessBuilder(argv)
            .directory(workDir.toFile())
            .redirectErrorStream(false)
            .start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (!allowNonZero && exitCode != 0) {
            throw GitException(command = argv, exitCode = exitCode, stderr = stderr.trim())
        }
        return ExecResult(exitCode, stdout, stderr)
    }
}

class GitException(
    val command: List<String>,
    val exitCode: Int,
    val stderr: String,
) : RuntimeException("${command.joinToString(" ")} exited $exitCode: $stderr")
