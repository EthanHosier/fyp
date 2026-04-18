package com.github.ethanhosier.analysis.normalize

import com.github.ethanhosier.analysis.model.Trace
import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.FileChangeType
import com.github.ethanhosier.ideplugin.model.FileSnapshot
import com.github.ethanhosier.ideplugin.model.SessionMetadata
import com.github.ethanhosier.ideplugin.model.TraceEvent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class TraceNormalizerTest {

    @Test
    fun `drops FILE_CREATED when REFACTORING_FINISHED already introduced the path`(@TempDir tmp: Path) {
        val projectPath = "/Users/dev/proj"
        val newFile = "$projectPath/src/main/java/org/example/EthanSuper.java"
        val initialSrc = Files.createDirectories(tmp.resolve("initial-src"))

        val trace = Trace(
            metadata = metadata(projectPath),
            events = listOf(
                event(
                    id = "e1",
                    type = EventType.REFACTORING_FINISHED,
                    timestamp = 100,
                    changedFiles = listOf(
                        FileSnapshot(newFile, contents = "class EthanSuper {}", changeType = FileChangeType.CREATED),
                    ),
                ),
                event(
                    id = "e2",
                    type = EventType.FILE_CREATED,
                    timestamp = 200,
                    changedFiles = listOf(
                        FileSnapshot(newFile, contents = "class EthanSuper {}", changeType = FileChangeType.CREATED),
                    ),
                ),
            ),
        )

        val result = TraceNormalizer.normalize(trace, initialSrc)

        assertEquals(listOf("e1"), result.events.map { it.id })
    }

    private fun metadata(projectPath: String) = SessionMetadata(
        sessionId = "sess",
        name = "t",
        projectName = "proj",
        projectPath = projectPath,
        startTime = 0,
        endTime = 1,
        ideVersion = "ij",
        pluginVersion = "pv",
    )

    private fun event(
        id: String,
        type: EventType,
        timestamp: Long,
        changedFiles: List<FileSnapshot>,
    ) = TraceEvent(
        id = id,
        type = type,
        timestamp = timestamp,
        sessionId = "sess",
        changedFiles = changedFiles,
    )
}
