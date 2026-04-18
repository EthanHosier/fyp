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

    @Test
    fun `absorbs whitespace-only EDIT_BURST fired just after REFACTORING_FINISHED`(@TempDir tmp: Path) {
        val projectPath = "/Users/dev/proj"
        val file = "$projectPath/src/main/java/org/example/Ethan.java"
        val initialSrc = Files.createDirectories(tmp.resolve("initial-src"))

        val pre = "public class Ethan {\n    public static void main(String[] args) {\n        noo();\n    }\n\n    }\n"
        val post = "public class Ethan {\n    public static void main(String[] args) {\n        noo();\n    }\n\n}\n"

        val trace = Trace(
            metadata = metadata(projectPath),
            events = listOf(
                event(
                    id = "r1",
                    type = EventType.REFACTORING_FINISHED,
                    timestamp = 100,
                    changedFiles = listOf(FileSnapshot(file, contents = pre, changeType = FileChangeType.MODIFIED)),
                ),
                event(
                    id = "b1",
                    type = EventType.EDIT_BURST,
                    timestamp = 104,
                    changedFiles = listOf(FileSnapshot(file, contents = post, changeType = FileChangeType.MODIFIED)),
                ),
            ),
        )

        val result = TraceNormalizer.normalize(trace, initialSrc)

        assertEquals(listOf("r1"), result.events.map { it.id })
        assertEquals(post, result.events.single().changedFiles.single().contents)
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
