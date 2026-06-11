package com.github.ethanhosier.analysis.reconstruct

import java.nio.file.Path

class GitRunner(private val workDir: Path) {

    fun version(): String = run("--version").trim()

    fun init() {
        run("init", "--quiet")
    }

    fun setLocalIdentity(email: String, name: String) {
        run("config", "--local", "user.email", email)
        run("config", "--local", "user.name", name)
    }

    fun addAll() {
        run("add", "-A")
    }

    fun addAllExcept(vararg pathspecs: String) {
        val args = mutableListOf("add", "-A", "--", ".")
        for (p in pathspecs) args += ":(exclude)$p"
        run(*args.toTypedArray())
    }

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

    fun commit(message: String): String {
        run("commit", "-m", message, "--allow-empty-message")
        return head()
    }

    fun head(): String = run("rev-parse", "HEAD").trim()

    fun stagedDiff(): String = run("diff", "--cached")

    fun resetHard() {
        run("reset", "--hard", "HEAD")
    }

    fun revParse(ref: String): String = run("rev-parse", ref).trim()

    fun worktreeAdd(path: Path, sha: String) {
        run("worktree", "add", "--detach", path.toAbsolutePath().toString(), sha)
    }

    fun worktreeRemove(path: Path) {
        run("worktree", "remove", "--force", path.toAbsolutePath().toString())
    }

    fun worktreePrune() {
        run("worktree", "prune")
    }

    fun checkoutDetach(sha: String) {
        run("checkout", "--detach", sha)
    }

    fun resetHard(sha: String) {
        run("reset", "--hard", sha)
    }

    fun resetSoft(sha: String) {
        run("reset", "--soft", sha)
    }

    sealed interface ApplyResult {
        data class Ok(val added: Int, val deleted: Int) : ApplyResult
        data class Conflict(
            val rejectedFiles: List<String>,
            val added: Int,
            val deleted: Int,
            val reason: String,
        ) : ApplyResult
    }

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

    fun applyDirect(patchFile: Path): ApplyResult {
        val result = exec(
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

    fun diffNumstat(from: String, to: String): List<String> =
        run("diff", "--numstat", "-M", from, to).lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()

    fun diffNameStatus(from: String, to: String): List<String> =
        run("diff", "--name-status", "-M", from, to).lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()

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

    fun branchForce(name: String, sha: String) {
        run("branch", "-f", name, sha)
    }

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

    fun parentOf(sha: String): String? {
        val result = exec(listOf("rev-parse", "--verify", "$sha^"), allowNonZero = true)
        return if (result.exitCode == 0) result.stdout.trim() else null
    }

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
