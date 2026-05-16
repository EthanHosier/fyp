package com.github.ethanhosier.analysis.experiment

import com.github.ethanhosier.analysis.metrics.derived.CleanlinessWeights
import com.github.ethanhosier.analysis.metrics.derived.ProcessScoreWeights
import com.github.ethanhosier.analysis.metrics.derived.ScoringConfig
import com.github.ethanhosier.analysis.pipeline.AnalysisReport
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

/**
 * Phase-2.2 sensitivity sweep. For each Phase-A dump in `--corpus`,
 * assemble a baseline report at [ScoringConfig.PRODUCTION], then perturb
 * each individual weight in [ProcessScoreWeights] and [CleanlinessWeights]
 * by ×0.5 and ×1.5, reassemble, and compare the resulting
 * divergence-point ranking against the baseline using
 * [RankingMetrics.kendallTauB] and [RankingMetrics.topNHitRate].
 *
 * ## How to use
 *
 * ```
 * ./gradlew :analysis:sensitivity \
 *     --args="--corpus <dir-of-phaseA-jsons> --output <results.csv>"
 * ```
 *
 * `--corpus` is a directory containing one or more `*.json` files; each
 * is a serialised [PhaseAResult] (produced by `:phaseA`). Output is a
 * single CSV with header [HEADER]; one row per (fixture × weight ×
 * factor). 24 rows per fixture (12 weights × 2 factors). Phase B is
 * cheap, so on N fixtures the sweep takes ~24N × ~50ms — well under
 * 30s for the planned corpus.
 *
 * ## How to interpret
 *
 * Each row reports how much *one* weight perturbation reshapes the
 * detector's divergence-point output relative to production weights.
 *
 *  - `kendall_tau` ∈ [-1, 1]: full-ranking correlation. 1.0 = identical
 *    order; near 0 = uncorrelated; negative = inverted.
 *  - `top5_hit_rate` ∈ [0, 1]: fraction of the top-5 *distinct* step
 *    indices on the baseline that also appear in the perturbed top 5.
 *  - `baseline_size` / `perturbed_size`: total divergence-point counts.
 *    Drops in `perturbed_size` mean the perturbation pushed some points
 *    below the magnitude floor (`DivergencePointBuilder.MIN_PROCESS_DELTA
 *    = 3.0`) rather than just reshuffling — useful to separate
 *    "different order" from "different membership."
 *
 * Reading the headline: if τ stays ≈1.0 across all 24 perturbations,
 * the choice of weights is *robust* (a defence). If τ collapses for
 * some weights at one direction but not the other, that's evidence of
 * asymmetric sensitivity — discuss in the methodology chapter.
 *
 * ## Things to note
 *
 *  - The 3.0 floor in [com.github.ethanhosier.analysis.divergence.DivergencePointBuilder]
 *    is a hard `const val`, not part of [ScoringConfig]. This sweep
 *    therefore tests *weight robustness above the fixed floor*, not
 *    floor robustness.
 *  - HYGIENE COMMIT_GAP divergence points are emitted **without** any
 *    magnitude filter (the builder gates them on existence of a hygiene
 *    info entry only). They appear in every perturbation's ranking in
 *    the same position — so a corpus dominated by COMMIT_GAP sessions
 *    will produce artificially stable τ. Per-kind breakdown in
 *    aggregation is the honest read.
 *  - REWORK magnitude is reverted-line-count, **weight-independent**.
 *    REWORK points only fall out of the list on `lineCount < 2`, never
 *    on weight perturbation.
 *  - Each row is *one knob moved in isolation*. Interaction effects
 *    (e.g. simultaneously halving `gain` and doubling `broken`) are out
 *    of scope; covered by [AblationExperiment] for the cumulative-zero
 *    case.
 */
object SensitivityExperiment {

    private val readJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val HEADER =
        "fixture,group,weight,factor,kendall_tau,top5_hit_rate,baseline_size,perturbed_size"

    // Data-driven sweep table: name + a (ScoringConfig, factor) ->
    // ScoringConfig perturbation that scales only the named weight.
    private data class Knob(
        val group: String,
        val name: String,
        val perturb: (ScoringConfig, Double) -> ScoringConfig,
    )

    private val KNOBS: List<Knob> = listOf(
        proc("gain") { p, f -> p.copy(gain = p.gain * f) },
        proc("broken") { p, f -> p.copy(broken = p.broken * f) },
        proc("skipTests") { p, f -> p.copy(skipTests = p.skipTests * f) },
        proc("manualIde") { p, f -> p.copy(manualIde = p.manualIde * f) },
        proc("length") { p, f -> p.copy(length = p.length * f) },
        proc("commitGap") { p, f -> p.copy(commitGap = p.commitGap * f) },
        clean("cognitive") { c, f -> c.copy(cognitive = c.cognitive * f) },
        clean("coupling") { c, f -> c.copy(coupling = c.coupling * f) },
        clean("duplication") { c, f -> c.copy(duplication = c.duplication * f) },
        clean("readability") { c, f -> c.copy(readability = c.readability * f) },
        clean("smells") { c, f -> c.copy(smells = c.smells * f) },
        clean("cohesion") { c, f -> c.copy(cohesion = c.cohesion * f) },
    )

    private val FACTORS = listOf(0.5, 1.5)

    private fun proc(
        name: String,
        scale: (ProcessScoreWeights, Double) -> ProcessScoreWeights,
    ): Knob = Knob("process", name) { cfg, f -> cfg.copy(process = scale(cfg.process, f)) }

    private fun clean(
        name: String,
        scale: (CleanlinessWeights, Double) -> CleanlinessWeights,
    ): Knob = Knob("cleanliness", name) { cfg, f -> cfg.copy(cleanliness = scale(cfg.cleanliness, f)) }

    @JvmStatic
    fun main(args: Array<String>) {
        val corpus = requireArg(args, "--corpus")?.let(Paths::get)
            ?: failUsage("missing --corpus")
        val output = requireArg(args, "--output")?.let(Paths::get)
            ?: failUsage("missing --output")

        if (!Files.isDirectory(corpus)) {
            System.err.println("sensitivity: --corpus is not a directory: $corpus")
            exitProcess(2)
        }

        val fixtures = Files.list(corpus).use { stream ->
            stream.filter { it.extension == "json" }.sorted().toList()
        }
        if (fixtures.isEmpty()) {
            System.err.println("sensitivity: no *.json fixtures in $corpus")
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
            "sensitivity: wrote ${rows.size - 1} rows across ${fixtures.size} fixtures " +
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
        val fixtureName = fixture.nameWithoutExtension

        val out = mutableListOf<String>()
        for (knob in KNOBS) {
            for (factor in FACTORS) {
                val perturbedCfg = knob.perturb(ScoringConfig.PRODUCTION, factor)
                val perturbedReport = ReportAssembler.assemble(phaseA, perturbedCfg)
                val perturbedRanking = ranking(perturbedReport)
                val tau = RankingMetrics.kendallTauB(baselineRanking, perturbedRanking)
                val hit = RankingMetrics.topNHitRate(baselineRanking, perturbedRanking, 5)
                out += listOf(
                    fixtureName,
                    knob.group,
                    knob.name,
                    String.format(Locale.US, "%.6f", factor),
                    String.format(Locale.US, "%.6f", tau),
                    String.format(Locale.US, "%.6f", hit),
                    baselineRanking.size.toString(),
                    perturbedRanking.size.toString(),
                ).joinToString(",")
            }
        }
        return out
    }

    private fun ranking(report: AnalysisReport): List<Int> =
        report.divergencePoints
            .sortedByDescending { it.magnitude }
            .map { it.stepIndex }

    private fun requireArg(args: Array<String>, flag: String): String? {
        val idx = args.indexOf(flag)
        if (idx < 0 || idx == args.lastIndex) return null
        return args[idx + 1]
    }

    private fun failUsage(reason: String): Nothing {
        System.err.println("sensitivity: $reason")
        System.err.println("usage: sensitivity --corpus <dir-of-phaseA-jsons> --output <results.csv>")
        exitProcess(2)
    }
}

fun main(args: Array<String>) {
    SensitivityExperiment.main(args)
}
