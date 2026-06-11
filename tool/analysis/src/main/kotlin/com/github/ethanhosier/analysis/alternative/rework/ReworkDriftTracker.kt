package com.github.ethanhosier.analysis.alternative.rework

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

    internal fun zoneSnapshot(): List<ZoneInfo> = zones.map {
        ZoneInfo(it.id, it.file, it.kind, it.userStartLine, it.length)
    }

    data class ZoneInfo(
        val id: Int, val file: String, val kind: Kind,
        val userStartLine: Int, val length: Int,
    )
}
