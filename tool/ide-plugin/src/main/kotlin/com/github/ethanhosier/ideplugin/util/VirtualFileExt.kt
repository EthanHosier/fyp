package com.github.ethanhosier.ideplugin.util

import com.intellij.openapi.vfs.VirtualFile

/**
 * True if this file should be captured by the tracer. We only track files inside
 * a `src/` folder (covers Gradle/Maven `src/main/...` and `src/test/...`, Android
 * `app/src/main/...`, etc.). Naturally excludes `.git/`, `.idea/`, build output,
 * `.refactoring-traces/`, config files, and anything opened from outside the
 * project source tree.
 */
fun VirtualFile.shouldCapture(): Boolean = !isDirectory && path.contains("/src/")
