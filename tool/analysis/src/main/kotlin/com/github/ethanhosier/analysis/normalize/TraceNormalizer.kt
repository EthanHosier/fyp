package com.github.ethanhosier.analysis.normalize

import com.github.ethanhosier.analysis.model.Trace
import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.FileChangeType
import com.github.ethanhosier.ideplugin.model.TraceEvent
import java.nio.file.Files
import java.nio.file.Path

object TraceNormalizer {
    private const val FORMATTING_BURST_WINDOW_MS = 100L

    fun normalize(trace: Trace, initialSrc: Path): Trace =
        trace
            .let(::sortByTimestamp)
            .let(::absorbPostRefactoringFormattingBursts)
            .let(::absorbDuplicateFileCreateContents)
            .let { dropDuplicateFileCreations(it, initialSrc) }

    private fun sortByTimestamp(trace: Trace): Trace =
        trace.copy(events = trace.events.sortedBy { it.timestamp })

    /**
     * IntelliJ often auto-formats the modified file immediately after a
     * refactoring, which surfaces as an EDIT_BURST a few ms after
     * REFACTORING_FINISHED touching only whitespace. Absorb it: replace the
     * refactoring snapshot's contents with the post-format text and drop
     * the burst event so the refactoring remains atomic in the trace.
     *
     * Triggers only when the burst: (a) immediately follows the refactoring
     * within [FORMATTING_BURST_WINDOW_MS], (b) touches only paths already in
     * the refactoring's changedFiles, and (c) differs from those snapshots
     * by whitespace alone.
     */
    private fun absorbPostRefactoringFormattingBursts(trace: Trace): Trace {
        val events = trace.events
        if (events.size < 2) return trace
        val out = ArrayList<TraceEvent>(events.size)
        var i = 0
        while (i < events.size) {
            val event = events[i]
            val next = events.getOrNull(i + 1)
            if (
                event.type == EventType.REFACTORING_FINISHED &&
                next != null &&
                next.type == EventType.EDIT_BURST &&
                next.timestamp - event.timestamp in 0..FORMATTING_BURST_WINDOW_MS &&
                isWhitespaceOnlyBurstOf(event, next)
            ) {
                out.add(mergeFormattingBurstInto(event, next))
                i += 2
            } else {
                out.add(event)
                i++
            }
        }
        return trace.copy(events = out)
    }

    private fun isWhitespaceOnlyBurstOf(refactoring: TraceEvent, burst: TraceEvent): Boolean {
        if (burst.changedFiles.isEmpty()) return false
        val refByPath = refactoring.changedFiles.associateBy { it.path }
        for (burstSnap in burst.changedFiles) {
            val refSnap = refByPath[burstSnap.path] ?: return false
            val refContents = refSnap.contents ?: return false
            val burstContents = burstSnap.contents ?: return false
            if (refContents.filterNot(Char::isWhitespace) != burstContents.filterNot(Char::isWhitespace)) {
                return false
            }
        }
        return true
    }

    private fun mergeFormattingBurstInto(refactoring: TraceEvent, burst: TraceEvent): TraceEvent {
        val burstByPath = burst.changedFiles.associateBy { it.path }
        val merged = refactoring.changedFiles.map { snap ->
            val formatted = burstByPath[snap.path] ?: return@map snap
            snap.copy(contents = formatted.contents)
        }
        return refactoring.copy(changedFiles = merged)
    }

    /**
     * NOTE: THIS IS A TEMP FIX. PROBS IS GOING TO BE BUGGY IN FUTURE -> WILL NEED
     * A SMARTER WAY TO HANDLE THE AUTO FORMATTING OF EDITOR.
     * The EDIT_BURST absorption above only catches files the user had open in
     * an editor — those are the ones whose auto-format triggers a DocumentEvent.
     * Files created by a refactoring that aren't open (e.g. the new superclass
     * from Extract Superclass) get formatted silently; the only signal is the
     * lagging VFS `FILE_CREATED` a few seconds later, which carries the
     * post-format text.
     *
     * This stage walks the trace and, for every `FILE_CREATED` whose path has
     * a prior snapshot somewhere in the trace with whitespace-equal contents,
     * replaces the prior snapshot's contents with the newer post-format text.
     * The `FILE_CREATED` event itself is left in place; the dedup pass drops
     * it immediately after.
     */
    private fun absorbDuplicateFileCreateContents(trace: Trace): Trace {
        val events = trace.events.toMutableList()
        val lastIndexByPath = HashMap<String, Int>()
        for (i in events.indices) {
            val event = events[i]
            if (event.type == EventType.FILE_CREATED) {
                for (snap in event.changedFiles) {
                    val priorIdx = lastIndexByPath[snap.path] ?: continue
                    val prior = events[priorIdx]
                    val priorSnapIdx = prior.changedFiles.indexOfFirst { it.path == snap.path }
                    if (priorSnapIdx < 0) continue
                    val priorContents = prior.changedFiles[priorSnapIdx].contents ?: continue
                    val newContents = snap.contents ?: continue
                    if (priorContents.filterNot(Char::isWhitespace) !=
                        newContents.filterNot(Char::isWhitespace)
                    ) continue
                    val updated = prior.changedFiles.toMutableList()
                    updated[priorSnapIdx] = updated[priorSnapIdx].copy(contents = newContents)
                    events[priorIdx] = prior.copy(changedFiles = updated)
                }
            }
            for (snap in event.changedFiles) lastIndexByPath[snap.path] = i
        }
        return trace.copy(events = events)
    }

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
