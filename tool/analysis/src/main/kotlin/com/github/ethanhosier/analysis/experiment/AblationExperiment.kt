package com.github.ethanhosier.analysis.experiment

import com.github.ethanhosier.analysis.metrics.derived.ProcessScoreWeights
import com.github.ethanhosier.analysis.metrics.derived.ScoringConfig
import com.github.ethanhosier.analysis.pipeline.AnalysisReport
import com.github.ethanhosier.analysis.pipeline.DivergencePoint
import com.github.ethanhosier.analysis.pipeline.PhaseAResult
import com.github.ethanhosier.analysis.pipeline.ReportAssembler
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.system.exitProcess

object AblationExperiment {

    private val readJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val HEADER =
        "fixture,variant,active_count,kendall_tau,top5_hit_rate,baseline_size,perturbed_size," +
            "baseline_saturated_dp_count,perturbed_saturated_dp_count," +
            "baseline_total_abs_magnitude,perturbed_total_abs_magnitude," +
            "magnitude_recovery_fraction,mean_abs_magnitude_delta,max_abs_magnitude_delta"

    private val PROCESS_KNOBS: List<Pair<String, (ProcessScoreWeights, Double) -> ProcessScoreWeights>> =
        listOf(
            "gain" to { p, v -> p.copy(gain = v) },
            "broken" to { p, v -> p.copy(broken = v) },
            "skipTests" to { p, v -> p.copy(skipTests = v) },
            "manualIde" to { p, v -> p.copy(manualIde = v) },
            "length" to { p, v -> p.copy(length = v) },
            "commitGap" to { p, v -> p.copy(commitGap = v) },
            "lag" to { p, v -> p.copy(lag = v) },
        )

    private val VARIANTS_POWERSET: List<Pair<String, ScoringConfig>> = run {
        val n = PROCESS_KNOBS.size
        (0 until (1 shl n)).map { mask ->
            val activeNames = mutableListOf<String>()
            // Start from PRODUCTION defaults; zero each inactive knob.
            var w = ProcessScoreWeights()
            for (i in 0 until n) {
                val (name, copier) = PROCESS_KNOBS[i]
                val active = (mask shr i) and 1 == 1
                if (active) activeNames += name else w = copier(w, 0.0)
            }
            val label = if (activeNames.isEmpty()) "(none)" else activeNames.joinToString("+")
            label to ScoringConfig.PRODUCTION.copy(process = w)
        }
    }


    @JvmStatic
    fun main(args: Array<String>) {
        val corpus = requireArg(args, "--corpus")?.let(Paths::get)
            ?: failUsage("missing --corpus")
        val output = requireArg(args, "--output")?.let(Paths::get)
            ?: failUsage("missing --output")

        if (!Files.isDirectory(corpus)) {
            System.err.println("ablation: --corpus is not a directory: $corpus")
            exitProcess(2)
        }

        val fixtures = Files.list(corpus).use { stream ->
            stream.filter { it.extension == "json" }.sorted().toList()
        }
        if (fixtures.isEmpty()) {
            System.err.println("ablation: no *.json fixtures in $corpus")
            exitProcess(2)
        }

        val started = System.currentTimeMillis()
        val rows = mutableListOf<String>()
        rows += HEADER
        for (fixture in fixtures) {
            rows += sweepFixture(fixture)
        }
        Files.writeString(output, rows.joinToString("\n", postfix = "\n"))
        val durationMs = System.currentTimeMillis() - started
        println(
            "ablation: wrote ${rows.size - 1} rows across ${fixtures.size} fixtures " +
                "to ${output.toAbsolutePath()} in ${durationMs}ms",
        )
    }

    private fun sweepFixture(fixture: Path): List<String> {
        val phaseA = readJson.decodeFromString(
            PhaseAResult.serializer(),
            Files.readString(fixture),
        )
        val baselineReport = ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)
        val baselineRanking = ranking(baselineReport)
        val baselineSat = saturatedDpCount(baselineReport)
        val baselineMags = baselineReport.divergencePoints.map { it.magnitude }
        val baselineTotalAbs = baselineMags.sumOf { kotlin.math.abs(it) }
        val fixtureName = fixture.nameWithoutExtension

        val out = mutableListOf<String>()
        for ((name, cfg) in VARIANTS_POWERSET) {
            val perturbedReport = ReportAssembler.assemble(phaseA, cfg)
            val perturbedRanking = ranking(perturbedReport)
            val tau = RankingMetrics.kendallTauB(baselineRanking, perturbedRanking)
            val hit = RankingMetrics.topNHitRate(baselineRanking, perturbedRanking, 5)
            val perturbedSat = saturatedDpCount(perturbedReport)
            val perturbedMags = perturbedReport.divergencePoints.map { it.magnitude }
            val perturbedTotalAbs = perturbedMags.sumOf { kotlin.math.abs(it) }
            val recovery = when {
                baselineTotalAbs > 0.0 -> perturbedTotalAbs / baselineTotalAbs
                perturbedTotalAbs == 0.0 -> 1.0
                else -> 0.0
            }
            val deltas = baselineMags.zip(perturbedMags) { b, p -> kotlin.math.abs(p - b) }
            val meanDelta = if (deltas.isEmpty()) 0.0 else deltas.average()
            val maxDelta = deltas.maxOrNull() ?: 0.0
            val activeCount = if (name == "(none)") 0 else name.count { it == '+' } + 1
            out += listOf(
                fixtureName,
                name,
                activeCount.toString(),
                String.format(Locale.US, "%.6f", tau),
                String.format(Locale.US, "%.6f", hit),
                baselineRanking.size.toString(),
                perturbedRanking.size.toString(),
                baselineSat.toString(),
                perturbedSat.toString(),
                String.format(Locale.US, "%.6f", baselineTotalAbs),
                String.format(Locale.US, "%.6f", perturbedTotalAbs),
                String.format(Locale.US, "%.6f", recovery),
                String.format(Locale.US, "%.6f", meanDelta),
                String.format(Locale.US, "%.6f", maxDelta),
            ).joinToString(",")
        }
        return out
    }

    // Ties broken by ascending stepIndex — see SensitivityExperiment.ranking
    // for the same rationale.
    private fun ranking(report: AnalysisReport): List<Int> =
        report.divergencePoints
            .sortedWith(
                compareByDescending<DivergencePoint> { it.magnitude }
                    .thenBy { it.stepIndex }
            )
            .map { it.stepIndex }

    private fun saturatedDpCount(report: AnalysisReport): Int {
        val userProcessBySha: Map<String, Int> = report.checkpoints
            .associate { it.sha to it.derivedMetrics.process.total }
        var saturated = 0
        for (dp in report.divergencePoints) {
            val alts = dp.altTrajectoryIndexes
                .mapNotNull { report.alternativeTrajectories.getOrNull(it) }
            val touchesBoundary = alts.any { alt ->
                val altTerm = alt.altCheckpoints.lastOrNull()?.derivedMetrics?.process?.total
                val userTerm = userProcessBySha[alt.userToSha]
                (altTerm != null && (altTerm == 0 || altTerm == 100)) ||
                    (userTerm != null && (userTerm == 0 || userTerm == 100))
            }
            if (touchesBoundary) saturated += 1
        }
        return saturated
    }

    private fun requireArg(args: Array<String>, flag: String): String? {
        val idx = args.indexOf(flag)
        if (idx < 0 || idx == args.lastIndex) return null
        return args[idx + 1]
    }

    private fun failUsage(reason: String): Nothing {
        System.err.println("ablation: $reason")
        System.err.println("usage: ablation --corpus <dir-of-phaseA-jsons> --output <results.csv>")
        exitProcess(2)
    }
}

fun main(args: Array<String>) {
    AblationExperiment.main(args)
}
