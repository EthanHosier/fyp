package com.github.ethanhosier.analysis.alternative.rework

/**
 * Tracks per-file divergence between the user's tree and the synthetic
 * (surgically-replayed) tree as a list of *zones*. A zone is a region
 * where the two trees disagree by a known number of lines:
 *
 *  - **ADDED-side zone** — `K` user lines starting at `userStartLine`
 *    that have *no synth equivalent* (surgery dropped them at the
 *    originating step). User lines past the zone are offset by `-K`
 *    in synth coords. Lines inside the zone translate to `null`
 *    (no synth equivalent).
 *
 *  - **REMOVED-side zone** — `K` synth lines starting at the
 *    position corresponding to `userStartLine` that have *no user
 *    equivalent* (the user removed them, but surgery preserved them).
 *    User lines at or past `userStartLine` are offset by `+K` in
 *    synth coords. No user line maps to `null` (REMOVED zones live
 *    on the synth side).
 *
 * Zones are registered at the originating step `k'` (when surgery
 * happens) and cleared at the terminal step `k` (when surgery
 * reverses them — the trees converge).
 *
 * As intermediate steps apply hunks to the *user* tree, zones whose
 * `userStartLine` is past the applied hunk shift by the hunk's net
 * line delta — this keeps the zone anchored at the same logical
 * content as the user's tree grows or shrinks.
 *
 * Pure state; no git, no filesystem.
 */
class ReworkDriftTracker {

    enum class Kind { ADDED, REMOVED }

    private data class Zone(
        val id: Int,
        val file: String,
        val kind: Kind,
        var userStartLine: Int,
        val length: Int,
    )

    private val zones = mutableListOf<Zone>()
    private var nextId = 0

    fun registerAddedZone(file: String, userStartLine: Int, length: Int): Int =
        register(file, Kind.ADDED, userStartLine, length)

    fun registerRemovedZone(file: String, userStartLine: Int, length: Int): Int =
        register(file, Kind.REMOVED, userStartLine, length)

    private fun register(file: String, kind: Kind, userStartLine: Int, length: Int): Int {
        require(length > 0) { "zone length must be positive" }
        val id = nextId++
        zones += Zone(id, file, kind, userStartLine, length)
        return id
    }

    fun clearZone(zoneId: Int) {
        zones.removeAll { it.id == zoneId }
    }

    /**
     * Translate a user-tree line number into synth-tree coords.
     * Returns `null` when [userLine] falls inside an ADDED-side zone
     * (no synth equivalent — the caller's hunk is untranslatable).
     */
    fun translate(file: String, userLine: Int): Int? {
        val fileZones = zones.filter { it.file == file }.sortedBy { it.userStartLine }
        var offset = 0
        for (z in fileZones) {
            if (userLine < z.userStartLine) break
            when (z.kind) {
                Kind.ADDED -> {
                    if (userLine < z.userStartLine + z.length) return null
                    offset -= z.length
                }
                Kind.REMOVED -> {
                    offset += z.length
                }
            }
        }
        return userLine + offset
    }

    /**
     * Translate the `oldStart` of a *pure-insertion* hunk (oldLen == 0).
     * Pure insertions specify "insert after user line oldStart" by
     * convention. If line oldStart is not in any zone, translate it
     * directly. If it is (insertion at the end of an ADDED zone),
     * scan forward to the first line past the zone and step back by
     * one — yielding "insert just before the post-zone first line in
     * synth," which is the correct boundary.
     *
     * Returns `null` only if the scan walks past a [scanLimit] of
     * lines without finding a non-zone position — defensive against
     * pathologically large zones; in practice the first non-zone line
     * is one past the zone end.
     */
    fun translateInsertionPoint(file: String, userOldStart: Int, scanLimit: Int = 100_000): Int? {
        translate(file, userOldStart)?.let { return it }
        var probe = userOldStart + 1
        var steps = 0
        while (steps < scanLimit) {
            translate(file, probe)?.let { return it - 1 }
            probe++
            steps++
        }
        return null
    }

    /**
     * Record that a hunk has just been applied to the user's tree at
     * `userOldStart` (the hunk's pre-image starting line), affecting
     * `oldLen` lines and producing `newLen` lines. Zones strictly
     * past the applied region shift their `userStartLine` by
     * `newLen - oldLen` — they hold the same content but at a new
     * line number.
     */
    fun recordHunkApplied(file: String, userOldStart: Int, oldLen: Int, newLen: Int) {
        val netDelta = newLen - oldLen
        if (netDelta == 0) return
        val affectedEnd = userOldStart + oldLen   // exclusive upper bound in user coords
        for (z in zones) {
            if (z.file != file) continue
            if (z.userStartLine >= affectedEnd) {
                z.userStartLine += netDelta
            }
        }
    }

    /** For tests: snapshot of current zone state. */
    internal fun zoneSnapshot(): List<ZoneInfo> = zones.map {
        ZoneInfo(it.id, it.file, it.kind, it.userStartLine, it.length)
    }

    data class ZoneInfo(
        val id: Int, val file: String, val kind: Kind,
        val userStartLine: Int, val length: Int,
    )
}
