package com.github.ethanhosier.ideplugin.http

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.LocalFileSystem
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.HttpRequestHandler
import java.nio.charset.StandardCharsets

/**
 * GET /api/refactoring-tracer/refresh-vfs
 *
 * Synchronously refreshes IntelliJ's LocalFileSystem so that file changes
 * made by an external process (e.g. an agent editing files outside the IDE)
 * are picked up without the user having to click "Sync Project". Intended
 * to be polled at a fixed interval (~0.5s) by the agent-session wrapper
 * script while a recording is in progress.
 *
 * The endpoint is served on IntelliJ's built-in HTTP server, default port
 * 63342. No auth, no CSRF — relies on the built-in server's localhost-only
 * default binding.
 */
class VfsRefreshHttpHandler : HttpRequestHandler() {

    override fun isSupported(request: FullHttpRequest): Boolean =
        request.uri().startsWith(PATH)

    override fun process(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext,
    ): Boolean {
        val start = System.currentTimeMillis()
        // refresh(asynchronous=false): blocks the Netty event-loop thread
        // until the VFS scan completes. The scan itself dispatches its
        // change-events to the EDT internally; we just need to wait for
        // them to finish so callers see a consistent view on return.
        LocalFileSystem.getInstance().refresh(false)
        val elapsedMs = System.currentTimeMillis() - start
        thisLogger().debug("VFS refresh completed in ${elapsedMs}ms")

        val body = """{"ok":true,"elapsedMs":$elapsedMs}""".toByteArray(StandardCharsets.UTF_8)
        val resp = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(body),
        )
        resp.headers().add("Content-Type", "application/json")
        resp.headers().add("Content-Length", body.size.toString())
        resp.headers().add("Cache-Control", "no-store")
        context.writeAndFlush(resp)
        return true
    }

    companion object {
        const val PATH = "/api/refactoring-tracer/refresh-vfs"
    }
}
