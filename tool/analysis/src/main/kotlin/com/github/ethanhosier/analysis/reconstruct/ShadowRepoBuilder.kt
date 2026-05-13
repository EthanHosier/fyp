package com.github.ethanhosier.analysis.reconstruct

import com.github.ethanhosier.analysis.diffs.DiffAnalysis
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.model.Trace
import com.github.ethanhosier.ideplugin.model.FileChangeType
import com.github.ethanhosier.ideplugin.model.FileSnapshot
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Replays a normalized [Trace] into a shadow git repo.
 *
 * Layout produced under the session folder:
 * ```
 * <sessionFolder>/
 * ├── shadow-repo/       ← git repo: baseline commit + one commit per state-bearing event
 * └── event-commits.json ← EventCommitMap: every event -> commit SHA
 * ```
 *
 * A state-bearing event is one whose `changedFiles` actually alters the
 * working tree relative to the prior commit. No-op events (empty
 * `changedFiles`, or edits that write the same bytes already on disk) map to
 * the most recent preceding SHA so every event resolves to a commit without
 * polluting the history with empty ones.
 *
 * Path resolution strips [com.github.ethanhosier.ideplugin.model.SessionMetadata.projectPath]
 * from each absolute `FileSnapshot.path` to find the repo-relative location.
 * Anything that tries to escape the repo root aborts.
 */
class ShadowRepoBuilder(
    private val json: Json = Json { prettyPrint = true; encodeDefaults = true },
) {
    fun build(sessionFolder: Path, trace: Trace): ReconstructionResult {
        val initialSrc = sessionFolder.resolve("initial-src")
        require(Files.isDirectory(initialSrc)) { "initial-src missing at $initialSrc" }

        val repoDir = sessionFolder.resolve("shadow-repo")
        if (Files.exists(repoDir)) repoDir.toFile().deleteRecursively()
        Files.createDirectories(repoDir)

        val git = GitRunner(repoDir)
        git.version()

        copyTree(initialSrc, repoDir)
        git.init()
        git.setLocalIdentity(email = "analysis@local", name = "analysis")
        git.addAll()
        git.commit("baseline: session ${trace.metadata.sessionId}")
        var previousSha = git.head()

        val mapping = LinkedHashMap<String, String>()
        val projectPrefix = trace.metadata.projectPath.trimEnd('/') + "/"

        for (event in trace.events) {
            if (event.changedFiles.isEmpty()) {
                mapping[event.id] = previousSha
                continue
            }

            for (snap in event.changedFiles) {
                applySnapshot(snap, repoDir, projectPrefix)
            }

            git.addAll()
            if (!git.hasStagedChanges()) {
                mapping[event.id] = previousSha
                continue
            }

            // Drop edits whose only effect is adding blank/whitespace-
            // only lines — they don't materially change the code and
            // would otherwise pollute the checkpoint stream with no-op
            // commits. We roll the working tree + index back to HEAD so
            // subsequent events apply on top of the pre-blank state.
            if (isBlankLineOnlyDiff(git.stagedDiff())) {
                git.resetHard()
                mapping[event.id] = previousSha
                continue
            }

            git.commit("${event.type} ${event.id} @ ${event.timestamp}")
            val sha = git.head()
            mapping[event.id] = sha
            previousSha = sha
        }

        val commitMap = EventCommitMap(mapping)
        Files.writeString(
            sessionFolder.resolve("event-commits.json"),
            json.encodeToString(EventCommitMap.serializer(), commitMap),
        )

        return ReconstructionResult(repoDir, commitMap)
    }

    private fun applySnapshot(snap: FileSnapshot, repoDir: Path, projectPrefix: String) {
        val target = resolveInRepo(repoDir, snap.path, projectPrefix)
        when (snap.changeType) {
            FileChangeType.CREATED, FileChangeType.MODIFIED -> {
                val contents = snap.contents ?: error("${snap.changeType} snapshot has null contents: ${snap.path}")
                Files.createDirectories(target.parent)
                Files.writeString(target, contents)
            }
            FileChangeType.DELETED -> {
                if (!Files.exists(target)) error("DELETED snapshot but file absent: $target")
                Files.delete(target)
            }
            FileChangeType.RENAMED, FileChangeType.MOVED -> {
                val prevAbs = snap.previousPath ?: error("${snap.changeType} snapshot has null previousPath: ${snap.path}")
                val source = resolveInRepo(repoDir, prevAbs, projectPrefix)
                if (!Files.exists(source)) error("${snap.changeType} source missing: $source")
                Files.createDirectories(target.parent)
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
                val contents = snap.contents ?: error("${snap.changeType} snapshot has null contents: ${snap.path}")
                Files.writeString(target, contents)
            }
        }
    }

    private fun resolveInRepo(repoDir: Path, absPath: String, projectPrefix: String): Path {
        require(absPath.startsWith(projectPrefix)) {
            "path '$absPath' does not start with project prefix '$projectPrefix'"
        }
        val relative = absPath.removePrefix(projectPrefix)
        val resolved = repoDir.resolve(relative).normalize()
        require(resolved.startsWith(repoDir.normalize())) {
            "resolved path escapes repo root: $resolved"
        }
        return resolved
    }

    private fun isBlankLineOnlyDiff(patch: String): Boolean =
        DiffAnalysis.isWhitespaceOnly(patch)

    private fun copyTree(source: Path, target: Path) {
        Files.walk(source).use { paths ->
            paths.forEach { src ->
                val rel = source.relativize(src)
                val dst = target.resolve(rel.toString())
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dst)
                } else {
                    Files.createDirectories(dst.parent)
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }
}
