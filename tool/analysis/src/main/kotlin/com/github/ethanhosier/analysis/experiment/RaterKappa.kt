package com.github.ethanhosier.analysis.experiment

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.system.exitProcess

object RaterKappa {

    private val KINDS = listOf("ORDERING", "IDE_REPLAY", "REWORK", "HYGIENE")

    @JvmStatic
    fun main(args: Array<String>) {
        val raters = collectRaters(args)
        if (raters.size != 2) {
            System.err.println("usage: raterKappa --rater <rater1.csv> --rater <rater2.csv>")
            exitProcess(2)
        }
        val r1Path = raters[0]
        val r2Path = raters[1]

        val r1 = loadManifest(r1Path)
        val r2 = loadManifest(r2Path)
        val sessions = (r1.keys intersect r2.keys).sorted()

        println("${r1Path.name} vs ${r2Path.name} — ${sessions.size} sessions in common")
        println()
        println(
            "%-12s %4s %9s %8s %8s %8s %7s %7s".format(
                "kind", "n", "both_yes", "both_no", "r1y_r2n", "r1n_r2y", "agree%", "kappa",
            )
        )
        for (kind in KINDS) {
            var yy = 0; var yn = 0; var ny = 0; var nn = 0
            for (sid in sessions) {
                val in1 = kind in (r1[sid] ?: emptySet())
                val in2 = kind in (r2[sid] ?: emptySet())
                when {
                    in1 && in2 -> yy++
                    in1 && !in2 -> yn++
                    !in1 && in2 -> ny++
                    else -> nn++
                }
            }
            val n = yy + yn + ny + nn
            val agree = if (n > 0) (yy + nn).toDouble() / n else 0.0
            val k = kappa(yy, yn, ny, nn)
            println(
                "%-12s %4d %9d %8d %8d %8d %7.3f %7.3f".format(
                    kind, n, yy, nn, yn, ny, agree, k,
                )
            )
        }
    }

    private fun loadManifest(path: Path): Map<String, Set<String>> {
        val lines = Files.readAllLines(path)
        if (lines.isEmpty()) return emptyMap()
        val header = parseCsvLine(lines[0])
        val sessionIdIdx = header.indexOf("session_id").also {
            require(it >= 0) { "no `session_id` column in $path" }
        }
        val expectedKindsIdx = header.indexOf("expected_kinds").also {
            require(it >= 0) { "no `expected_kinds` column in $path" }
        }
        val out = linkedMapOf<String, Set<String>>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            val row = parseCsvLine(line)
            if (row.size <= maxOf(sessionIdIdx, expectedKindsIdx)) continue
            val sid = row[sessionIdIdx]
            val kindsField = row[expectedKindsIdx]
            val kinds = kindsField.split(";").mapNotNull { it.trim().ifEmpty { null } }.toSet()
            out[sid] = kinds
        }
        return out
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"')
                        i += 2
                        continue
                    }
                    inQuotes = false
                } else {
                    cur.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> { out += cur.toString(); cur.setLength(0) }
                    else -> cur.append(c)
                }
            }
            i++
        }
        out += cur.toString()
        return out
    }

    private fun kappa(yy: Int, yn: Int, ny: Int, nn: Int): Double {
        val n = yy + yn + ny + nn
        if (n == 0) return Double.NaN
        val po = (yy + nn).toDouble() / n
        val r1Yes = (yy + yn).toDouble() / n
        val r2Yes = (yy + ny).toDouble() / n
        val pe = r1Yes * r2Yes + (1 - r1Yes) * (1 - r2Yes)
        if (pe == 1.0) return if (po == 1.0) 1.0 else Double.NaN
        return (po - pe) / (1 - pe)
    }

    private fun collectRaters(args: Array<String>): List<Path> {
        val out = mutableListOf<Path>()
        var i = 0
        while (i < args.size) {
            if (args[i] == "--rater" && i + 1 < args.size) {
                out.add(Paths.get(args[i + 1]))
                i += 2
            } else {
                i++
            }
        }
        return out
    }
}

fun main(args: Array<String>) {
    RaterKappa.main(args)
}
