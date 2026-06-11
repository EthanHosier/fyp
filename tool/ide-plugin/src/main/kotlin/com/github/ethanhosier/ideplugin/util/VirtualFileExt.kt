package com.github.ethanhosier.ideplugin.util

import com.intellij.openapi.vfs.VirtualFile

fun VirtualFile.shouldCapture(): Boolean =
    !isDirectory && path.contains("/src/") && !fileType.isBinary
