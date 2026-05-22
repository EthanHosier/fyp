package com.github.ethanhosier.analysis.experiment

import com.github.ethanhosier.analysis.pipeline.AnalysisReport
import com.github.ethanhosier.analysis.pipeline.DivergenceKind
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Per-kind divergence-point trajectory across the six-session arc, one
 * block per participant plus a combined-cohort block. Mirrors the
 * legacy Python `scripts/per-kind-trajectory.py`.
 *
 * Counts are restricted to `report.divergencePoints[]` with strict
 * `magnitude > 0`, matching the downstream consumer at
 * `DivergenceExperiment.kt:273`.
 */
object PerKindTrajectory {

    private val KINDS = listOf(
        DivergenceKind.IDE_REPLAY,
        DivergenceKind.HYGIENE,
        DivergenceKind.ORDERING,
        DivergenceKind.REWORK,
    )

    private val readJson = Json { ignoreUnknownKeys = true; isLenient = true }

    @JvmStatic
    fun main(args: Array<String>) {
        val root = requireArg(args, "--root")?.let(Paths::get)
            ?: Paths.get("fixtures/user-sessions")

        // by_participant[participant][session_idx] = {kind -> count}
        val byParticipant = sortedMapOf<String, MutableMap<Int, Map<DivergenceKind, Int>>>()
        Files.list(root).use { stream ->
            for (d in stream.sorted().toList()) {
                if (!d.isDirectory()) continue
                val parsed = parseSessionName(d.name) ?: continue
                val (participant, idx) = parsed
                val counts = loadSession(d) ?: continue
                byParticipant.getOrPut(participant) { mutableMapOf() }[idx] = counts
            }
        }

        // Per-participant block: kind × sessions + delta + trend.
        for ((participant, sessions) in byParticipant) {
            val maxIdx = sessions.keys.max()
            println()
            println("=== $participant ===")
            println()
            val header = buildString {
                append("kind".padEnd(12))
                for (i in 1..maxIdx) append("S%2d".format(i).padStart(6))
                append("   delta(S6-S1)   trend")
            }
            println(header)
            for (kind in KINDS) {
                val perSession = (1..maxIdx).map { i -> sessions[i]?.get(kind) ?: 0 }
                val row = StringBuilder()
                row.append(kind.name.padEnd(12))
                for (c in perSession) row.append("%6d".format(c))
                val delta = perSession.last() - perSession.first()
                val trend = trendOf(perSession)
                row.append("   %+5d          %s".format(delta, trend))
                println(row.toString())
            }
        }

        // Combined block: per-kind, sum across participants.
        println()
        println()
        println("=== Both participants combined (sum per session) ===")
        println()
        val combinedHeader = buildString {
            append("kind".padEnd(12))
            for (i in 1..6) append("S%2d".format(i).padStart(6))
            append("   delta(S6-S1)")
        }
        println(combinedHeader)
        for (kind in KINDS) {
            val perSession = (1..6).map { i ->
                byParticipant.values.sumOf { sessions -> sessions[i]?.get(kind) ?: 0 }
            }
            val row = StringBuilder()
            row.append(kind.name.padEnd(12))
            for (c in perSession) row.append("%6d".format(c))
            row.append("   %+5d".format(perSession.last() - perSession.first()))
            println(row.toString())
        }
    }

    // Accepts `<participant>-<NN>` (user-sessions) and `<NN>-<participant>`
    // (agent-sessions, e.g. `01-agent`).
    private val NAME_RE_USER = Regex("^([a-z]+)-(\\d+)$")
    private val NAME_RE_AGENT = Regex("^(\\d+)-([a-z]+)$")

    private fun parseSessionName(name: String): Pair<String, Int>? {
        NAME_RE_USER.matchEntire(name)?.let {
            return it.groupValues[1] to it.groupValues[2].toInt()
        }
        NAME_RE_AGENT.matchEntire(name)?.let {
            return it.groupValues[2] to it.groupValues[1].toInt()
        }
        return null
    }

    private fun loadSession(d: Path): Map<DivergenceKind, Int>? {
        val reportPath = locateReport(d) ?: return null
        val report = readJson.decodeFromString(
            AnalysisReport.serializer(),
            Files.readString(reportPath),
        )
        return KINDS.associateWith { k ->
            report.divergencePoints.count { it.kind == k && it.magnitude > 0.0 }
        }
    }

    private fun locateReport(d: Path): Path? {
        val direct = d.resolve("analysis-report.json")
        if (direct.isRegularFile()) return direct
        Files.list(d).use { stream ->
            for (child in stream) {
                if (!child.isDirectory()) continue
                val candidate = child.resolve("analysis-report.json")
                if (candidate.isRegularFile()) return candidate
            }
        }
        return null
    }

    private fun trendOf(perSession: List<Int>): String {
        if (perSession.all { it == 0 }) return "—"
        val mid = perSession.size / 2
        val firstHalf = perSession.take(mid).sum()
        val secondHalf = perSession.drop(mid).sum()
        return when {
            firstHalf > secondHalf -> "↓ declining"
            firstHalf < secondHalf -> "↑ rising"
            else -> "≈ flat"
        }
    }

    private fun requireArg(args: Array<String>, name: String): String? {
        val i = args.indexOf(name)
        if (i < 0 || i + 1 >= args.size) return null
        return args[i + 1]
    }
}

fun main(args: Array<String>) {
    PerKindTrajectory.main(args)
}
