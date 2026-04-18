package com.github.ethanhosier.analysis.normalize

import com.github.ethanhosier.analysis.model.Trace
import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.FileChangeType
import com.github.ethanhosier.ideplugin.model.TraceEvent
import java.nio.file.Files
import java.nio.file.Path

object TraceNormalizer {

    fun normalize(trace: Trace, initialSrc: Path): Trace =
        trace
            .let(::sortByTimestamp)
            .let { dropDuplicateFileCreations(it, initialSrc) }
            .let { dropNoOpEditBursts(it, initialSrc) }

    private fun sortByTimestamp(trace: Trace): Trace =
        trace.copy(events = trace.events.sortedBy { it.timestamp })

    /**
     * Drops `FILE_CREATED` events whose every path was already live at that
     * point in the stream. This filters the lagging VFS `FILE_CREATED` that
     * fires a few seconds after a refactoring has already captured the new
     * file inside its own `REFACTORING_FINISHED` — the event is semantically
     * a duplicate and pollutes the checkpoint report and refactoring-miner
     * segmentation.
     *
     * "Live" is seeded from `initial-src/` and updated per event using each
     * snapshot's `changeType`, so a legitimate re-create after `DELETED`
     * correctly survives.
     */
    private fun dropDuplicateFileCreations(trace: Trace, initialSrc: Path): Trace {
        val liveFiles = seedFromInitialSrc(initialSrc, trace.metadata.projectPath)
        val kept = ArrayList<TraceEvent>(trace.events.size)
        for (event in trace.events) {
            val paths = event.changedFiles.map { it.path }
            val duplicateCreate = event.type == EventType.FILE_CREATED &&
                paths.isNotEmpty() &&
                paths.all { it in liveFiles }
            if (duplicateCreate) continue
            for (snap in event.changedFiles) {
                when (snap.changeType) {
                    FileChangeType.DELETED -> liveFiles.remove(snap.path)
                    FileChangeType.RENAMED, FileChangeType.MOVED -> {
                        snap.previousPath?.let { liveFiles.remove(it) }
                        liveFiles.add(snap.path)
                    }
                    FileChangeType.CREATED, FileChangeType.MODIFIED -> liveFiles.add(snap.path)
                }
            }
            kept.add(event)
        }
        return trace.copy(events = kept)
    }

    /**
     * Drops `EDIT_BURST` snapshots whose contents match the file's prior known
     * state — stray bursts that IntelliJ occasionally emits when focus changes
     * or formatters touch a document without actually mutating it. If every
     * snapshot on a burst is a no-op, the whole event is dropped.
     *
     * Prior state is tracked across the stream. On a cache miss, the file is
     * loaded lazily from `initial-src/` so we only pay for files events
     * actually reference.
     */
    private fun dropNoOpEditBursts(trace: Trace, initialSrc: Path): Trace {
        val prefix = trace.metadata.projectPath.trimEnd('/')
        val knownContent = HashMap<String, String?>()

        fun priorContent(path: String): String? {
            if (knownContent.containsKey(path)) return knownContent[path]
            val rel = path.removePrefix("$prefix/")
            val seedPath = initialSrc.resolve(rel)
            val loaded = runCatching {
                if (Files.isRegularFile(seedPath)) Files.readString(seedPath) else null
            }.getOrNull()
            knownContent[path] = loaded
            return loaded
        }

        val kept = ArrayList<TraceEvent>(trace.events.size)
        for (event in trace.events) {
            val isEditBurst = event.type == EventType.EDIT_BURST
            val filteredSnaps = if (!isEditBurst) event.changedFiles else event.changedFiles.filter { snap ->
                snap.contents != priorContent(snap.path)
            }

            if (isEditBurst && filteredSnaps.isEmpty()) {
                // Update nothing; the burst is a pure no-op against known state.
                continue
            }

            for (snap in event.changedFiles) {
                when (snap.changeType) {
                    FileChangeType.DELETED -> knownContent[snap.path] = null
                    FileChangeType.RENAMED, FileChangeType.MOVED -> {
                        snap.previousPath?.let { knownContent[it] = null }
                        knownContent[snap.path] = snap.contents
                    }
                    FileChangeType.CREATED, FileChangeType.MODIFIED -> knownContent[snap.path] = snap.contents
                }
            }

            kept.add(if (isEditBurst && filteredSnaps.size != event.changedFiles.size) event.copy(changedFiles = filteredSnaps) else event)
        }
        return trace.copy(events = kept)
    }

    private fun seedFromInitialSrc(initialSrc: Path, projectPath: String): MutableSet<String> {
        val out = HashSet<String>()
        if (!Files.isDirectory(initialSrc)) return out
        val prefix = projectPath.trimEnd('/')
        Files.walk(initialSrc).use { stream ->
            stream.forEach { p ->
                if (Files.isRegularFile(p)) {
                    val rel = initialSrc.relativize(p).toString()
                    out.add("$prefix/$rel")
                }
            }
        }
        return out
    }
}
