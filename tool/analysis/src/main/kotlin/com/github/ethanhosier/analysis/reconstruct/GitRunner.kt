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
