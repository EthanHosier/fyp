package com.github.ethanhosier.ideplugin.services

import com.github.ethanhosier.ideplugin.model.CheckpointFile
import com.github.ethanhosier.ideplugin.model.FileChangeType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which files have changed since the last checkpoint.
 *
 * Thread-safe: listeners on different threads call markDirty(); CheckpointService
 * calls getDirtyFiles() + reset() under its own synchronization.
 */
@Service(Service.Level.PROJECT)
class FileStateTracker {

    // Keyed by canonical path. Later changes overwrite earlier ones for the same path,
    // which is correct — we only need the final change type at checkpoint time.
    private val dirtyFiles: ConcurrentHashMap<String, DirtyEntry> = ConcurrentHashMap()

    data class DirtyEntry(
        val path: String,
        val changeType: FileChangeType,
        val previousPath: String? = null,
    )

    fun markDirty(path: String, changeType: FileChangeType, previousPath: String? = null) {
        dirtyFiles[path] = DirtyEntry(path, changeType, previousPath)
        thisLogger().debug("RefactoringTracer: marked dirty path=$path type=$changeType")
    }

    /**
     * Returns all dirty files, resolving their current contents from disk.
     * Call [reset] afterwards to clear the tracking state.
     */
    fun getDirtyFiles(): List<CheckpointFile> {
        return dirtyFiles.values.map { entry ->
            val contents = if (entry.changeType != FileChangeType.DELETED) {
                readFileContents(entry.path)
            } else {
                null
            }
            CheckpointFile(
                path = entry.path,
                fullContents = contents,
                changeType = entry.changeType,
                previousPath = entry.previousPath,
            )
        }
    }

    fun reset() {
        dirtyFiles.clear()
    }

    fun hasDirtyFiles(): Boolean = dirtyFiles.isNotEmpty()

    private fun readFileContents(path: String): String? {
        return try {
            java.io.File(path).readText()
        } catch (e: Exception) {
            thisLogger().warn("RefactoringTracer: could not read file contents path=$path: ${e.message}")
            null
        }
    }
}
