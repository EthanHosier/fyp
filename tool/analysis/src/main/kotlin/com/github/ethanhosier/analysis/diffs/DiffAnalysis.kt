package com.github.ethanhosier.analysis.diffs

/**
 * Pure helpers over unified-diff text. Kept dependency-free so any
 * pipeline stage (reconstruction, rework synthesis, …) can call in
 * without dragging in heavier diff machinery.
 */
object DiffAnalysis {
    /**
     * Returns `true` when every modifying line in [patch] is a
     * whitespace-only `+` addition or `-` removal — i.e. the patch's
     * only effect is reshuffling blank/whitespace lines. File-header
     * lines (`diff --git`, `index ...`, `--- a/...`, `+++ b/...`,
     * `@@ ... @@`) are ignored; any `+` or `-` line carrying
     * non-whitespace content fails the check. An empty / header-only
     * patch returns `false` so callers don't accidentally treat
     * "nothing to compare" as "whitespace-only".
     */
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
