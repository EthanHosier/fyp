package com.github.ethanhosier.analysis.experiment

import com.github.ethanhosier.analysis.metrics.derived.CleanlinessWeights
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
 *    With the magnitude floor removed from `DivergencePointBuilder`,
 *    these are now driven purely by alt-synthesis success — drops in
 *    `perturbed_size` should be rare and indicate an alt that failed
 *    to synthesise under the perturbed weights (e.g. a knock-on through
 *    `wasPerformedByIde` classification). Use kendall_tau as the
 *    primary signal of "did the perturbation reshuffle the ranking?"
 *
 * Reading the headline: if τ stays ≈1.0 across all 24 perturbations,
 * the choice of weights is *robust* (a defence). If τ collapses for
 * some weights at one direction but not the other, that's evidence of
 * asymmetric sensitivity — discuss in the methodology chapter.
 *
 * ## Things to note
 *
 *  - There is no longer a magnitude floor in
 *    [com.github.ethanhosier.analysis.divergence.DivergencePointBuilder]
 *    (b03a7ba removed it). Every synthesised alt produces a DP — this
 *    sweep therefore measures pure rank perturbation, no threshold
 *    cliffs. Per-kind reporting downstream can apply a post-hoc
 *    magnitude threshold if needed.
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
        "fixture,group,weight,factor,kendall_tau," +
            "top1_hit_rate,top3_hit_rate,top5_hit_rate,top10_hit_rate," +
            "baseline_size,perturbed_size," +
            "baseline_saturated_dp_count,perturbed_saturated_dp_count"

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

    private val FACTORS = listOf(0.1, 0.25, 0.5, 0.75, 1.25, 1.5, 2.0, 4.0, 10.0)

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
        val baselineSat = saturatedDpCount(baselineReport)
        val fixtureName = fixture.nameWithoutExtension

        val out = mutableListOf<String>()
        for (knob in KNOBS) {
            for (factor in FACTORS) {
                val perturbedCfg = knob.perturb(ScoringConfig.PRODUCTION, factor)
                val perturbedReport = ReportAssembler.assemble(phaseA, perturbedCfg)
                val perturbedRanking = ranking(perturbedReport)
                val tau = RankingMetrics.kendallTauB(baselineRanking, perturbedRanking)
                val hit1 = RankingMetrics.topNHitRate(baselineRanking, perturbedRanking, 1)
                val hit3 = RankingMetrics.topNHitRate(baselineRanking, perturbedRanking, 3)
                val hit5 = RankingMetrics.topNHitRate(baselineRanking, perturbedRanking, 5)
                val hit10 = RankingMetrics.topNHitRate(baselineRanking, perturbedRanking, 10)
                val perturbedSat = saturatedDpCount(perturbedReport)
                out += listOf(
                    fixtureName,
                    knob.group,
                    knob.name,
                    String.format(Locale.US, "%.6f", factor),
                    String.format(Locale.US, "%.6f", tau),
                    String.format(Locale.US, "%.6f", hit1),
                    String.format(Locale.US, "%.6f", hit3),
                    String.format(Locale.US, "%.6f", hit5),
                    String.format(Locale.US, "%.6f", hit10),
                    baselineRanking.size.toString(),
                    perturbedRanking.size.toString(),
                    baselineSat.toString(),
                    perturbedSat.toString(),
                ).joinToString(",")
            }
        }
        return out
    }

    // Ties on magnitude (HYGIENE-COMMIT_GAP DPs all carry magnitude
    // exactly W_cg by construction; saturated DPs all share the
    // clamp-induced magnitude) are broken by ascending stepIndex so
    // the ranking is a deterministic function of the report rather
    // than relying on the stable-sort artefact of insertion order.
    private fun ranking(report: AnalysisReport): List<Int> =
        report.divergencePoints
            .sortedWith(
                compareByDescending<DivergencePoint> { it.magnitude }
                    .thenBy { it.stepIndex }
            )
            .map { it.stepIndex }

    /**
     * Number of divergence points whose terminal user- or alt-process
     * score sits at the [0, 100] clamp boundary. A high count means τ
     * understates true sensitivity — those DPs have weight-invariant
     * magnitudes (clipping eats the perturbation), so the ranking can
     * look stable when it shouldn't. A DP is counted as saturated if
     * **any** of its referenced alts' terminal score OR the matching
     * user-terminal score is at 0 or 100.
     */
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
        System.err.println("sensitivity: $reason")
        System.err.println("usage: sensitivity --corpus <dir-of-phaseA-jsons> --output <results.csv>")
        exitProcess(2)
    }
}

fun main(args: Array<String>) {
    SensitivityExperiment.main(args)
}
