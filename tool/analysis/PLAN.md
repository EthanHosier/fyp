# Analysis Server Entrypoint

## Context

Today the analysis pipeline runs as a CLI: `./gradlew :analysis:run --args="<sessionFolder>"` loads the session from disk, computes metrics, and writes `analysis-report.json` alongside the inputs. We want to move metric computation off the IDE machine eventually — step one is to extract the pipeline behind an HTTP server that the ide-plugin posts to at session end. For now the server runs locally (separate JVM) next to the IDE; the same binary will later deploy remotely without code changes.

Two refactors this forces:

1. **Extract the pipeline from the CLI.** `cli/Main.kt` currently threads load → normalize → reconstruct → metrics → report-writing together. Needs to become a reusable `AnalysisPipeline.run(sessionDir)` that both CLI and server call.
2. **Separate "input dir" from "write dir."** Today `ShadowRepoBuilder` and `MetricsRunner` both read from and write into the same `sessionFolder`. The server can't write into the plugin's user-owned folder (and won't have it at all once remote), so all pipeline writes must land in a caller-supplied scratch dir. For the CLI we pass the session folder as both input and scratch — behaviour is unchanged. For the server we pass a `Files.createTempDirectory()`.

## Approach

### New `:server` module (Ktor + Netty)

- `server/build.gradle.kts`: depends on `:analysis` (transitively `:shared`), pulls in Ktor server + content negotiation + kotlinx.serialization.
- `server/src/main/kotlin/com/github/ethanhosier/server/Main.kt`: `embeddedServer(Netty, port = 8080) { ... }` with a single route.
- Two endpoints:
  - `GET /health` — returns `200 OK` with `{"status": "ok"}`. Trivial liveness probe; the plugin uses it on startup to decide whether to offer analysis (skip otherwise) and ops tooling uses it once we deploy remotely.
  - `POST /analyze` — accepts `multipart/form-data` with three named parts:
    - `session.json` → deserialize to `Session`
    - `initial-src.zip` → unzip into `<tempDir>/initial-src/`
    - `executable-paths.json` → JSON array of relative paths; apply `+x` to each unzipped file
- For `POST /analyze`: server writes `session.json` + generates `events.jsonl` (one line per event) into `<tempDir>` so the existing `TraceLoader` works unchanged, invokes `AnalysisPipeline.run(tempDir)`, returns the resulting `AnalysisReport` as `application/json`.
- `tempDir` is cleaned up in a `finally` regardless of outcome. Startup logs "listening on :8080".
- Add a `server/src/main/kotlin/.../AnalyzeRoute.kt` so the Ktor wiring and the pipeline invocation are separable/testable.

### `:analysis` refactor

**New file:** `analysis/src/main/kotlin/com/github/ethanhosier/analysis/pipeline/AnalysisPipeline.kt`

```kotlin
class AnalysisPipeline(private val parallelism: Int = defaultParallelism()) {
    data class Result(
        val trace: Trace,
        val reconstruction: ReconstructionResult,
        val metrics: MetricsRunner.Summary,
        val metricsDurationMs: Long,
        val report: AnalysisReport,
    )
    fun run(sessionDir: Path): Result { /* load → normalize → reconstruct → metrics → build report */ }
}
```

- Moves the `writeAnalysisReport` logic (currently in `cli/Main.kt:115-155`) into the pipeline as pure in-memory report construction. The pipeline does **not** write `analysis-report.json` — that's the caller's job (CLI writes it; server returns it in the response body).
- CLI (`cli/Main.kt`) shrinks to: parse args → `pipeline.run(sessionFolder)` → `Files.writeString(sessionFolder.resolve("analysis-report.json"), Json.encodeToString(result.report))` → print the summary lines it already prints today.
- `MetricsRunner`'s output dir (`<sessionDir>/checkpoint-metrics/`) and `ShadowRepoBuilder`'s output dir (`<sessionDir>/shadow-repo/`) stay rooted at the supplied path — no change to those, they already accept a `Path`.
- Delete `saveNormalizedTraceToJson` in `cli/Main.kt` (CLI-only debug dump — writes to `src/main/kotlin/.../cli/normalized-events.jsonl`, which is gitignored scratch; no production code reads it).
- Mark `Trace` `@Serializable` (currently the only domain type crossing module boundaries that isn't). Leave `ReconstructionResult` as-is — it holds a `Path` and stays server-internal.

### `:ide-plugin` additions

**New file:** `ide-plugin/src/main/kotlin/com/github/ethanhosier/ideplugin/services/AnalysisClient.kt`

- Project-level `@Service` that owns a `java.net.http.HttpClient` (JDK built-in, no new dep).
- `fun upload(sessionDir: Path): AnalysisReport`:
  - Reads `session.json` (already written by `StorageService.flushSession`).
  - Streams a ZIP of `<sessionDir>/initial-src/` into a `ByteArray` via `ZipOutputStream`.
  - Walks `initial-src/` and collects paths where `Files.isExecutable` is true → `executable-paths.json`.
  - Builds a multipart body (small custom boundary writer — the JDK HttpClient has no built-in multipart, but the body shape is ~30 lines and well-known).
  - POSTs to `serverUrl`, deserializes the `AnalysisReport` response.
- Server URL: `System.getenv("REFACTORING_TRACER_SERVER_URL") ?: "http://localhost:8080"` computed once at service init.

**Hook point:** `SessionService.endSession()` at `services/SessionService.kt:137-163`. Immediately after the existing `flushSession(session)` call on line 161, kick off a `Task.Backgroundable`:

```kotlin
ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Analysing session…", true) {
    override fun run(indicator: ProgressIndicator) {
        val report = project.service<AnalysisClient>().upload(sessionDir)
        Files.writeString(sessionDir.resolve("analysis-report.json"), json.encodeToString(report))
        Notifications.Bus.notify(Notification(...).addAction(RevealFileAction(reportPath)), project)
    }
})
```

- `sessionDir` comes from `StorageService` — need a public getter (`fun currentSessionDir(): Path?`) since it's currently private. Add one; don't leak `File` objects.
- Failure surfaces as an error notification with the exception message; no retry logic for now.
- A new notification group `"RefactoringTracer.Analysis"` registered in `plugin.xml` so the toast is themeable/silenceable.

### Wire contract (for reference)

```
GET /health HTTP/1.1

→ 200 OK
  Content-Type: application/json
  {"status": "ok"}
```

```
POST /analyze HTTP/1.1
Content-Type: multipart/form-data; boundary=<b>

--<b>
Content-Disposition: form-data; name="session.json"; filename="session.json"
Content-Type: application/json

{ ...Session... }
--<b>
Content-Disposition: form-data; name="initial-src.zip"; filename="initial-src.zip"
Content-Type: application/zip

<zip bytes>
--<b>
Content-Disposition: form-data; name="executable-paths.json"; filename="executable-paths.json"
Content-Type: application/json

["gradlew"]
--<b>--
```

Response: `200 OK` with `application/json` body = `AnalysisReport`. Errors: `400` for malformed upload, `500` with `{error, message}` for pipeline failure.

## Critical files

**New:**
- `server/build.gradle.kts`
- `server/src/main/kotlin/com/github/ethanhosier/server/Main.kt`
- `server/src/main/kotlin/com/github/ethanhosier/server/AnalyzeRoute.kt`
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/pipeline/AnalysisPipeline.kt`
- `ide-plugin/src/main/kotlin/com/github/ethanhosier/ideplugin/services/AnalysisClient.kt`

**Modified:**
- `settings.gradle.kts` — add `include(":server")`.
- `gradle/libs.versions.toml` — add Ktor version + `ktor-server-core`, `ktor-server-netty`, `ktor-server-content-negotiation`, `ktor-serialization-kotlinx-json`.
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/cli/Main.kt` — shrink to a thin wrapper over `AnalysisPipeline`; drop the report-building + `saveNormalizedTraceToJson` helpers.
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/model/Trace.kt` — add `@Serializable`.
- `ide-plugin/src/main/kotlin/com/github/ethanhosier/ideplugin/services/SessionService.kt` — after `flushSession` (line 161), spawn the upload `Task.Backgroundable`.
- `ide-plugin/src/main/kotlin/com/github/ethanhosier/ideplugin/services/StorageService.kt` — add a public `currentSessionDir(): Path?` getter.
- `ide-plugin/src/main/resources/META-INF/plugin.xml` — register `AnalysisClient` service + the new notification group.

## Reused utilities (worth calling out)

- `TraceLoader`, `TraceNormalizer`, `ShadowRepoBuilder`, `MetricsRunner` — consumed as-is by `AnalysisPipeline`; no changes to their signatures.
- `Session` / `TraceEvent` / `FileSnapshot` / `AnalysisReport` / `CheckpointMetrics` and downstream result types — already `@Serializable`, no work needed.
- Existing `StorageService.writeSessionFile` (with the executable-bit flag) already records which files need `+x` on the plugin side — but we'll recompute at upload time via `Files.isExecutable`, which is authoritative regardless of how the file was written.

## Verification

1. **Unit-ish tests**
   - `server/src/test/kotlin/.../AnalyzeRouteTest.kt` using Ktor's `testApplication`: build a multipart body from a minimal canned session folder, POST it, assert a non-null `AnalysisReport` comes back with the expected number of checkpoints.
   - `analysis/src/test/kotlin/.../pipeline/AnalysisPipelineTest.kt`: reuse the same canned session folder, assert `pipeline.run(dir)` produces the same report shape.

2. **Manual end-to-end** (local)
   ```
   # terminal 1
   ./gradlew :server:run
   # → "listening on :8080"
   curl -s http://localhost:8080/health   # → {"status":"ok"}

   # terminal 2 — open IntelliJ w/ the plugin installed, run a session,
   # click End Session, watch for the "Analysing session…" progress bar and
   # the "Analysis complete — reveal report" notification.
   ```
   Confirm `<projectDir>/.refactoring-traces/<sessionId>/analysis-report.json` appears after the notification fires, matches what the CLI would produce for the same folder.

3. **CLI regression**
   ```
   ./gradlew :analysis:run --args="<sessionFolder>" -q
   ```
   Output should be identical to today's output apart from the dropped `normalized-events.jsonl` debug file.

## Non-goals (deferred)

- Auth / TLS — server is localhost-only for now.
- Streaming / chunked upload — multipart in one request is fine for ~100–500MB sessions.
- Settings UI for server URL — env var is enough until we deploy remotely.
- Results panel in the tool window — notification + reveal-in-finder is the current UX; a real UI is a separate follow-up.
- `POST /analyze` idempotency cache across uploads — `MetricsRunner`'s per-SHA `checkpoint-metrics/<sha>.json` cache still works *within* a request (same temp dir) but cross-request caching is out of scope; adding it later means keying the cache by SHA in a server-owned persistent dir.
