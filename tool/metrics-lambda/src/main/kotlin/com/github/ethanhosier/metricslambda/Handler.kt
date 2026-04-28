package com.github.ethanhosier.metricslambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import com.github.ethanhosier.analysis.metrics.CheckpointMetricsComputer
import com.github.ethanhosier.analysis.metrics.dto.ComputeRequest
import com.github.ethanhosier.analysis.metrics.dto.ComputeResponse
import kotlinx.serialization.json.Json
import software.amazon.awssdk.services.s3.S3Client
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path

/**
 * AWS Lambda entry point for remote checkpoint compute.
 *
 * Flow per invocation:
 *  1. Decode [ComputeRequest] from the event payload.
 *  2. Materialise a project worktree on the lambda's filesystem
 *     ([BundleMaterialiser]) — downloads the shadow-repo bundle from S3
 *     (cached across warm invocations) and `git worktree add`s the SHA.
 *  3. Run [CheckpointMetricsComputer.compute] against that worktree.
 *  4. Encode [ComputeResponse] to the output stream.
 *
 * Stream-based handler (rather than POJO `RequestHandler<I, O>`) so we
 * own the JSON codec end-to-end with `kotlinx.serialization` instead of
 * fighting the runtime's default Jackson mapper over `data class`
 * constructors.
 *
 * Init-time work (computer + S3 client + JSON codec construction)
 * happens once per JVM and is therefore cheap on warm invocations and
 * — once SnapStart is enabled in Terraform — captured in the snapshot
 * so cold starts skip class-loading entirely.
 */
class Handler : RequestStreamHandler {

    // Lazy so SnapStart's snapshot includes initialised PMD/CK class
    // loaders; on warm invocations these are already there too.
    private val computer = CheckpointMetricsComputer()
    private val s3: S3Client = S3Client.builder().build()
    private val json = Json { ignoreUnknownKeys = true }
    private val materialiser = BundleMaterialiser(s3, cacheRoot = Path.of("/tmp"))

    override fun handleRequest(input: InputStream, output: OutputStream, context: Context) {
        val payload = input.readBytes().decodeToString()
        val request = json.decodeFromString(ComputeRequest.serializer(), payload)
        val response = compute(request, context)
        output.write(json.encodeToString(ComputeResponse.serializer(), response).toByteArray())
    }

    private fun compute(request: ComputeRequest, context: Context): ComputeResponse {
        val log = context.logger
        log.log("compute: sha=${request.sha} bundle=${request.bundle.bucket}/${request.bundle.key}")
        return runCatching {
            val workDir = materialiser.materialise(request)
            val metrics = computer.compute(workDir, request.sha)
            ComputeResponse(sha = request.sha, metrics = metrics)
        }.getOrElse { t ->
            log.log("compute: failed sha=${request.sha}: ${t.stackTraceToString()}")
            ComputeResponse(sha = request.sha, error = t.stackTraceToString())
        }
    }
}
