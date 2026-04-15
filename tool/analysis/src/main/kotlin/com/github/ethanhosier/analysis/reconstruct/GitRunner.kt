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
