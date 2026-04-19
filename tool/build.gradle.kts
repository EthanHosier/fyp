plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.intelliJPlatform) apply false
    alias(libs.plugins.changelog) apply false
    alias(libs.plugins.qodana) apply false
    alias(libs.plugins.kover) apply false
}

tasks.wrapper {
    gradleVersion = providers.gradleProperty("gradleVersion").get()
}

/**
 * Run Plugin & Servers — convenience task for local development.
 *
 * Starts the `:analysis` HTTP server and the `dashboard/` Vite dev server as
 * child processes, waits for both to become reachable, then launches the
 * `:ide-plugin` sandbox IDE with stdio attached to this terminal. When the
 * sandbox IDE exits (or the task is interrupted), both servers are torn down.
 * Server output goes to `build/dev-logs/server.log` and
 * `build/dev-logs/vite.log`.
 *
 * Usage: `./gradlew runPluginAndServers`
 */
tasks.register("runPluginAndServers") {
    group = "application"
    description = "Run Plugin & Servers: starts analysis + Vite, then launches the sandbox IDE with the plugin."
    // Spawns child processes and waits on them — inherently stateful, so
    // configuration cache can't serialize/replay this task.
    notCompatibleWithConfigurationCache("Spawns and waits on external processes.")

    doLast {
        val logDir = layout.buildDirectory.dir("dev-logs").get().asFile.also { it.mkdirs() }
        val serverLog = logDir.resolve("server.log")
        val viteLog = logDir.resolve("vite.log")
        val gradlew = rootDir.resolve("gradlew").absolutePath

        logger.lifecycle("[run] starting analysis server — log: $serverLog")
        val server = ProcessBuilder(gradlew, ":analysis:runServer", "--console=plain", "-q")
            .directory(rootDir)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.to(serverLog))
            .start()
        Runtime.getRuntime().addShutdownHook(Thread { server.destroy() })

        logger.lifecycle("[run] starting Vite dev server — log: $viteLog")
        val vite = ProcessBuilder("npm", "run", "dev")
            .directory(rootDir.resolve("dashboard"))
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.to(viteLog))
            .start()
        Runtime.getRuntime().addShutdownHook(Thread { vite.destroy() })

        fun pollUrl(url: String, timeoutMs: Long, serviceName: String, process: Process, log: java.io.File): Boolean {
            val target = java.net.URI.create(url).toURL()
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (!process.isAlive) {
                    throw GradleException("$serviceName exited before becoming reachable; see $log")
                }
                try {
                    val conn = (target.openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 1000
                        readTimeout = 1000
                        instanceFollowRedirects = true
                    }
                    val code = conn.responseCode
                    // Vite responds 200; be lenient and accept anything < 500 as "up".
                    if (code in 200..499) return true
                } catch (_: Exception) {
                    // not up yet
                }
                Thread.sleep(500)
            }
            return false
        }

        val serverReady = pollUrl("http://localhost:8080/health", 60_000, "analysis server", server, serverLog)
        if (!serverReady) {
            server.destroy(); vite.destroy()
            throw GradleException("analysis server did not answer /health within 60s; see $serverLog")
        }
        // Vite 7 binds only to IPv6 `::1` by default on macOS — Java's resolver
        // picks IPv4 for `localhost` and doesn't fall through, so we probe the
        // literal IPv6 loopback. JCEF/Chromium handles dual-stack fine, so the
        // dashboard itself still loads via `http://localhost:5173`.
        val viteReady = pollUrl("http://[::1]:5173", 60_000, "Vite dev server", vite, viteLog)
        if (!viteReady) {
            server.destroy(); vite.destroy()
            throw GradleException("Vite dev server did not become reachable on :5173 within 60s; see $viteLog")
        }
        logger.lifecycle("[run] servers healthy (analysis :8080, vite :5173) — launching sandbox IDE")

        try {
            val ide = ProcessBuilder(gradlew, ":ide-plugin:runIde", "--console=plain")
                .directory(rootDir)
                .inheritIO()
                .start()
            ide.waitFor()
        } finally {
            logger.lifecycle("[run] stopping dev servers")
            server.destroy()
            vite.destroy()
            if (!server.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) server.destroyForcibly()
            if (!vite.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) vite.destroyForcibly()
        }
    }
}
