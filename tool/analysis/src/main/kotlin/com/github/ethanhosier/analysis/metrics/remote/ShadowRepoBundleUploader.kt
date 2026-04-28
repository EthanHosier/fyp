package com.github.ethanhosier.analysis.metrics.remote

import com.github.ethanhosier.analysis.metrics.dto.BundleRef
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Bundles a shadow repo into a single `git bundle` blob, content-addresses
 * the bytes (sha256), and uploads to S3 once per session — subsequent
 * sessions whose bundle hashes to the same key skip the upload entirely.
 *
 * The bundle covers `--all` refs, so synthetic alt-trajectory branches
 * (`alt/<i>`) ride along automatically.
 *
 * S3 naming: `shadow-bundles/<sha256>.bundle`. Bucket is expected to have
 * a lifecycle rule that expires these objects after a few days — the
 * pipeline only needs them while the session's lambdas are running.
 */
class ShadowRepoBundleUploader(
    private val s3: S3Client,
    private val bucket: String,
    private val keyPrefix: String = "shadow-bundles/",
    private val bundleTimeout: java.time.Duration = java.time.Duration.ofMinutes(2),
) {

    /** Bundle, hash, upload-if-absent, return ref. */
    fun uploadOnce(repoDir: Path): BundleRef {
        val bundleBytes = createBundle(repoDir)
        val key = keyPrefix + sha256Hex(bundleBytes) + ".bundle"
        if (!objectExists(key)) {
            s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).build(),
                RequestBody.fromBytes(bundleBytes),
            )
        }
        return BundleRef(bucket = bucket, key = key)
    }

    private fun objectExists(key: String): Boolean = try {
        s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
        true
    } catch (_: NoSuchKeyException) {
        false
    }

    /**
     * Run `git bundle create - --all` in [repoDir] and capture stdout. We
     * stream straight to memory because shadow-repo bundles for typical
     * Java sessions are MB-scale, well under any reasonable RAM ceiling
     * — and S3's PutObject is happiest with a fully-known content length.
     */
    private fun createBundle(repoDir: Path): ByteArray {
        val process = ProcessBuilder("git", "bundle", "create", "-", "--all")
            .directory(repoDir.toFile())
            .redirectErrorStream(false)
            .start()
        val bytes = process.inputStream.use { it.readAllBytes() }
        // Drain stderr so the process can exit even on big bundles.
        val stderr = process.errorStream.use { String(it.readAllBytes()) }
        if (!process.waitFor(bundleTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly().waitFor()
            error("git bundle timed out after $bundleTimeout in $repoDir")
        }
        if (process.exitValue() != 0) {
            error("git bundle failed (exit ${process.exitValue()}) in $repoDir: $stderr")
        }
        return bytes
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
