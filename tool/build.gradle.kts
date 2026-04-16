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
 * Run Plugin & Server — convenience task for local development.
 *
 * Starts the `:analysis` server as a child process, waits for it to
 * answer `/health`, then launches the `:ide-plugin` sandbox IDE with
 * stdio attached to this terminal. When the sandbox IDE exits (or the
 * task is interrupted), the server is torn down. Server output goes to
 * `build/dev-logs/server.log`.
 *
 * Usage: `./gradlew runPluginAndServer`
 */
tasks.register("runPluginAndServer") {
    group = "application"
    description = "Run Plugin & Server: starts the analysis server, then launches the sandbox IDE with the plugin."

    doLast {
        val logDir = layout.buildDirectory.dir("dev-logs").get().asFile.also { it.mkdirs() }
        val serverLog = logDir.resolve("server.log")
        val gradlew = rootDir.resolve("gradlew").absolutePath

        logger.lifecycle("[run] starting analysis server — log: $serverLog")
        val server = ProcessBuilder(gradlew, ":analysis:runServer", "--console=plain", "-q")
            .directory(rootDir)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.to(serverLog))
            .start()

        Runtime.getRuntime().addShutdownHook(Thread { server.destroy() })

        val healthUrl = java.net.URI.create("http://localhost:8080/health").toURL()
        val deadline = System.currentTimeMillis() + 60_000
        var ready = false
        while (System.currentTimeMillis() < deadline) {
            if (!server.isAlive) {
                throw GradleException("analysis server exited before becoming healthy; see $serverLog")
            }
            try {
                val conn = (healthUrl.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 1000
                    readTimeout = 1000
                }
                if (conn.responseCode == 200) { ready = true; break }
            } catch (_: Exception) {
                // server not up yet — keep polling
            }
            Thread.sleep(1000)
        }
        if (!ready) {
            server.destroy()
            throw GradleException("analysis server did not answer /health within 60s; see $serverLog")
        }
        logger.lifecycle("[run] server healthy on :8080 — launching sandbox IDE")

        try {
            val ide = ProcessBuilder(gradlew, ":ide-plugin:runIde", "--console=plain")
                .directory(rootDir)
                .inheritIO()
                .start()
            ide.waitFor()
        } finally {
            logger.lifecycle("[run] stopping analysis server")
            server.destroy()
            if (!server.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                server.destroyForcibly()
            }
        }
    }
}
