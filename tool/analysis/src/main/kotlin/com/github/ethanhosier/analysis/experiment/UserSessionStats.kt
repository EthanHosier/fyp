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
 * Per-session divergence-point counts by kind for a corpus of recorded
 * sessions (defaults to `fixtures/user-sessions`). Emits the same three
 * sections as the legacy Python `scripts/user-session-stats.py`:
 * per-session table, per-participant trajectory, per-kind aggregate.
 *
 * Counts are restricted to `report.divergencePoints[]` (not
 * `alternativeTrajectories[]`) with a strict `magnitude > 0` filter, to
 * match the downstream consumer at `DivergenceExperiment.kt:273`.
 */
object UserSessionStats {

    private val KINDS = listOf(
        DivergenceKind.IDE_REPLAY,
        DivergenceKind.REWORK,
        DivergenceKind.HYGIENE,
        DivergenceKind.ORDERING,
    )

    private val readJson = Json { ignoreUnknownKeys = true; isLenient = true }

    @JvmStatic
    fun main(args: Array<String>) {
        val root = requireArg(args, "--root")?.let(Paths::get)
            ?: Paths.get("fixtures/user-sessions")

        val sessions = mutableListOf<Session>()
        Files.list(root).use { stream ->
            for (d in stream.sorted().toList()) {
                if (!d.isDirectory()) continue
                val parsed = parseSessionName(d.name) ?: continue
                val (participant, idx) = parsed
                val reportPath = locateReport(d) ?: run {
                    System.err.println("WARN: no analysis-report under $d")
                    continue
                }
                val report = readJson.decodeFromString(
                    AnalysisReport.serializer(),
                    Files.readString(reportPath),
                )
                val counts = KINDS.associateWith { k ->
                    report.divergencePoints.count { it.kind == k && it.magnitude > 0.0 }
                }
                sessions += Session(participant, idx, d.name, counts)
            }
        }

        println()
        println(
            "%-12s %11s %7s %8s %9s %6s".format(
                "session", "IDE_REPLAY", "REWORK", "HYGIENE", "ORDERING", "total",
            )
        )
        for (s in sessions) {
            val total = s.counts.values.sum()
            println(
                "%-12s %11d %7d %8d %9d %6d".format(
                    s.name,
                    s.counts[DivergenceKind.IDE_REPLAY],
                    s.counts[DivergenceKind.REWORK],
                    s.counts[DivergenceKind.HYGIENE],
                    s.counts[DivergenceKind.ORDERING],
                    total,
                )
            )
        }

        println()
        println("=== Per-participant trajectory (DP counts per session) ===")
        val byParticipant = sessions
            .groupBy { it.participant }
            .toSortedMap()
        for ((participant, rows) in byParticipant) {
            println()
            println("$participant:")
            for (s in rows.sortedBy { it.idx }) {
                val total = s.counts.values.sum()
                val breakdown = KINDS
                    .filter { (s.counts[it] ?: 0) > 0 }
                    .joinToString(" ") { "${it.name}=${s.counts[it]}" }
                val parenthesised = breakdown.ifEmpty { "none" }
                println("  session ${s.idx}: $total DPs ($parenthesised)")
            }
        }

        println()
        println("=== Aggregate (all ${sessions.size} sessions) ===")
        val agg = KINDS.associateWith { k -> sessions.sumOf { it.counts[k] ?: 0 } }
        val totalAll = agg.values.sum()
        println("  total DPs: $totalAll")
        for (k in KINDS) {
            val n = agg.getValue(k)
            val share = if (totalAll > 0) n.toDouble() / totalAll else 0.0
            println("  %-12s %4d (%s)".format(k.name, n, formatPercent(share)))
        }
    }

    // Accepts `<participant>-<NN>` (user-sessions, e.g. `p1-01`, `p4-baseline-01`) and
    // `<NN>-<participant>` (agent-sessions, e.g. `01-agent`).
    private val NAME_RE_USER = Regex("^([a-z][a-z0-9-]*?)-(\\d+)$")
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

    private fun formatPercent(share: Double): String {
        // Match Python's `{share:.1%}` formatting (e.g. 46.5%).
        val pct = share * 100.0
        return "%.1f%%".format(pct)
    }

    private fun requireArg(args: Array<String>, name: String): String? {
        val i = args.indexOf(name)
        if (i < 0 || i + 1 >= args.size) return null
        return args[i + 1]
    }

    private data class Session(
        val participant: String,
        val idx: Int,
        val name: String,
        val counts: Map<DivergenceKind, Int>,
    )
}

fun main(args: Array<String>) {
    UserSessionStats.main(args)
}
