package com.github.ethanhosier.analysis.metrics.remote

import com.github.ethanhosier.analysis.metrics.dto.ComputeRequest
import com.github.ethanhosier.analysis.metrics.dto.ComputeResponse
import kotlinx.serialization.json.Json
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.InvokeRequest

/**
 * Indirection between [RemoteCheckpointExecutor] and the actual Lambda
 * client. Lets unit tests substitute a fake invoker without bringing the
 * AWS SDK into the test classpath.
 */
fun interface LambdaInvoker {
    fun invoke(request: ComputeRequest): ComputeResponse
}

/**
 * Real invoker: synchronously calls AWS Lambda's `Invoke` API for the
 * function identified by [functionArn]. Synchronous (rather than `InvokeAsync`)
 * because the caller already wraps invocations in its own thread pool — the
 * JVM-level concurrency is what gives us the fan-out.
 *
 * Construction does not validate the ARN; an unreachable function surfaces
 * as a thrown `LambdaException` on the first [invoke].
 */
class AwsLambdaInvoker(
    private val client: LambdaClient,
    private val functionArn: String,
    private val json: Json = DEFAULT_JSON,
) : LambdaInvoker {
    override fun invoke(request: ComputeRequest): ComputeResponse {
        val payload = json.encodeToString(ComputeRequest.serializer(), request)
        val response = client.invoke(
            InvokeRequest.builder()
                .functionName(functionArn)
                .payload(SdkBytes.fromUtf8String(payload))
                .build(),
        )
        // `functionError` is non-null when the handler threw or timed out.
        // The payload in that case is an AWS-shaped error envelope, not a
        // ComputeResponse — surface it as the response's `error` field so
        // the caller can attribute the failure to a SHA.
        if (response.functionError() != null) {
            return ComputeResponse(
                sha = request.sha,
                error = "lambda function error (${response.functionError()}): " +
                    response.payload().asUtf8String(),
            )
        }
        return json.decodeFromString(
            ComputeResponse.serializer(),
            response.payload().asUtf8String(),
        )
    }

    companion object {
        private val DEFAULT_JSON = Json { ignoreUnknownKeys = true }
    }
}
