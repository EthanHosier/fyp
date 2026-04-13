package com.github.ethanhosier.ideplugin.util

import com.intellij.openapi.vfs.VirtualFile

fun VirtualFile.isTracesFile() = path.contains("/.refactoring-traces/")
