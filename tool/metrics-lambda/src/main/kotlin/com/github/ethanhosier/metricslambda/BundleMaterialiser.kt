package com.github.ethanhosier.metricslambda

import com.github.ethanhosier.analysis.metrics.dto.ComputeRequest
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Prepares a project directory on the lambda's local filesystem at the
 * SHA in a [ComputeRequest], so
 * [com.github.ethanhosier.analysis.metrics.CheckpointMetricsComputer]
 * can run against it.
 *
 * Layout under [cacheRoot] (typically `/tmp` — 10 GB ephemeral storage
 * that persists across warm invocations on the same container):
 *
 * ```
 * /tmp/<bundle-sha>/
 *   bundle           ← raw bundle bytes
 *   repo/            ← `git clone` of the bundle; working tree retargeted per invocation
 * ```
 *
 * Per invocation:
 *  - If we've never seen this bundle hash on this container before
 *    (the typical case for a *new session* — whether or not the container
 *    is cold; warm containers can be reused across sessions): download
 *    bundle from S3, `git clone` it. ~1-3 s for typical sessions.
 *  - Always: `git checkout <sha>` to retarget the working tree, then
 *    `git clean -fdx` to wipe `build/` left over from any prior
 *    invocation. Both fast (<100 ms each).
 *
 * The per-bundle subdirectory layout means a container that serves
 * invocations from multiple sessions (which Lambda will happily do once
 * it has a warm pool) keeps each session's clone isolated rather than
 * thrashing one repo dir between bundle hashes.
 *
 * Worktrees aren't needed: a Lambda execution environment processes one
 * invocation at a time — concurrent invocations always get separate
 * containers — so sequential `git checkout` against a single repo dir
 * has no race.
 */
class BundleMaterialiser(
    private val s3: S3Client,
    private val cacheRoot: Path,
    private val gitTimeout: java.time.Duration = java.time.Duration.ofMinutes(2),
) {

    /** Returns the path of `repo/` checked out at `request.sha`, working tree clean. */
    fun materialise(request: ComputeRequest): Path {
        val bundleDir = cacheRoot.resolve(safeKey(request.bundle.key))
        val repoDir = bundleDir.resolve("repo")
        if (!Files.isDirectory(repoDir)) {
            ensureBundleDownloaded(request, bundleDir)
            cloneBundle(bundleDir, repoDir)
        }
        checkout(repoDir, request.sha)
        cleanWorkingTree(repoDir)
        return repoDir
    }

    private fun ensureBundleDownloaded(request: ComputeRequest, bundleDir: Path) {
        Files.createDirectories(bundleDir)
        val bundleFile = bundleDir.resolve("bundle")
        if (Files.isRegularFile(bundleFile)) return
        s3.getObject(
            GetObjectRequest.builder()
                .bucket(request.bundle.bucket)
                .key(request.bundle.key)
                .build(),
            bundleFile,
        )
    }

    private fun cloneBundle(bundleDir: Path, repoDir: Path) {
        val bundleFile = bundleDir.resolve("bundle")
        runGit(
            cwd = bundleDir,
            args = listOf("clone", bundleFile.toAbsolutePath().toString(), repoDir.toAbsolutePath().toString()),
            description = "git clone bundle",
        )
    }

    private fun checkout(repoDir: Path, sha: String) {
        runGit(
            cwd = repoDir,
            args = listOf("checkout", "--detach", sha),
            description = "git checkout $sha",
        )
    }

    /**
     * `-fdx` = force, include untracked dirs, include gitignored files.
     * Crucially wipes `build/` from a prior invocation so the next
     * `./gradlew build` doesn't see another SHA's leftover artefacts. The
     * `~/.gradle/caches/` dependency cache lives at HOME, not inside the
     * project, so it survives.
     */
    private fun cleanWorkingTree(repoDir: Path) {
        runGit(
            cwd = repoDir,
            args = listOf("clean", "-fdx"),
            description = "git clean",
        )
    }

    private fun runGit(cwd: Path, args: List<String>, description: String) {
        val cmd = listOf("git") + args
        val process = ProcessBuilder(cmd)
            .directory(cwd.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.use { String(it.readAllBytes()) }
        if (!process.waitFor(gitTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly().waitFor()
            error("$description timed out: $cmd")
        }
        if (process.exitValue() != 0) {
            error("$description failed (exit ${process.exitValue()}): $output")
        }
    }

    /**
     * S3 object keys may contain `/`; strip them so the cache subdir is a
     * single segment. The full key is sha-256-derived already, so dropping
     * the prefix is lossless for collision purposes.
     */
    private fun safeKey(key: String): String =
        key.substringAfterLast('/').substringBeforeLast('.')
}
