package com.github.ethanhosier.analysis.diffs

object DiffAnalysis {
    fun isWhitespaceOnly(patch: String): Boolean {
        if (patch.isBlank()) return false
        var sawModification = false
        for (line in patch.lineSequence()) {
            when {
                line.startsWith("+++") || line.startsWith("---") -> continue
                line.startsWith("diff --git") || line.startsWith("index ") -> continue
                line.startsWith("@@") -> continue
                line.startsWith("+") || line.startsWith("-") -> {
                    if (line.substring(1).isNotBlank()) return false
                    sawModification = true
                }
            }
        }
        return sawModification
    }
}
