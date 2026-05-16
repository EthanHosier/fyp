package com.github.ethanhosier.analysis.experiment

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
 * Phase-2.3 ablation sweep. For each Phase-A dump in `--corpus`,
 * assemble a baseline report at [ScoringConfig.PRODUCTION], then for
 * each variant in [VARIANTS], zero a cumulative subset of process-score
 * weights, reassemble, and compare the resulting divergence-point
 * ranking to the baseline using [RankingMetrics.kendallTauB] and
 * [RankingMetrics.topNHitRate].
 *
 * `cleanliness` group weights are kept at production defaults across
 * all variants — this sweep ablates only the process-score components.
 *
 * ## How to use
 *
 * ```
 * ./gradlew :analysis:ablation \
 *     --args="--corpus <dir-of-phaseA-jsons> --output <results.csv>"
 * ```
 *
 * Same corpus shape as [SensitivityExperiment]. Output is one CSV with
 * header [HEADER]; 5 rows per fixture, one per variant. Phase B is
 * cheap, so the sweep is sub-second per fixture.
 *
 * Variants (cumulative; each adds one term back relative to the
 * previous):
 *
 *  - `cleanlinessOnly`     — only `gain` (cleanliness Δ) active.
 *  - `plusBroken`          — + `broken`.
 *  - `plusHygieneTriplet`  — + `skipTests` + `manualIde` + `commitGap`.
 *  - `plusLength`          — + `length`. (Identical to `full` today —
 *                            the length term sits at the same weight in
 *                            production; the variant is kept as a
 *                            deliberate sanity row.)
 *  - `full`                — every term active. Must equal baseline:
 *                            τ = 1.0, top5_hit_rate = 1.0. If it
 *                            doesn't, the comparison plumbing is broken.
 *
 * ## How to interpret
 *
 * With the magnitude floor removed from `DivergencePointBuilder`, every
 * synthesised alt produces a DP regardless of magnitude — so
 * `perturbed_size` no longer drops when weights are zeroed (alt
 * existence is weight-independent). The meaningful signal is **τ**
 * (rank correlation) plus the **magnitude distribution** of each
 * variant's DPs vs the baseline's.
 *
 * For headline defence "which terms contribute to the score?" — read
 * per-fixture magnitudes per variant: `cleanlinessOnly` should have
 * small/zero magnitudes on penalty-driven divergence points, while
 * `full` recovers them. Use a downstream aggregation step (notebook,
 * R, whatever) to compute mean |magnitude| per variant per kind.
 *
 * τ tells you whether *ordering* changed; magnitude tells you whether
 * the *strength of the signal* changed. Both matter for the ablation
 * story.
 *
 * ## Things to note
 *
 *  - REWORK magnitude is reverted-line count, weight-independent —
 *    its DPs and magnitudes are identical across every variant. Not a
 *    calibration win, just the REWORK detector firing unconditionally.
 *  - HYGIENE COMMIT_GAP magnitude is structurally 0 (cadence isn't in
 *    the score formula yet), so every variant sees the same 0 there.
 *  - `plusLength == full` today: the production `length` weight is
 *    nonzero and `plusLength` keeps it, so they coincide. The variant
 *    documents intent and acts as a redundant sanity check; revise
 *    only if `length` is ever moved out of `ProcessScoreWeights`'s
 *    default-active set.
 *  - The `cleanliness` group is **not** ablated here; if a reviewer
 *    asks for cleanliness-sub-signal ablation, that needs a sibling
 *    variant list zeroing [com.github.ethanhosier.analysis.metrics.derived.CleanlinessWeights]
 *    fields.
 */
object AblationExperiment {

    private val readJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val HEADER =
        "fixture,variant,kendall_tau,top5_hit_rate,baseline_size,perturbed_size"

    // Cumulative ablation: each variant zeroes a superset of the
    // previous variant's zeroed weights. `full` matches PRODUCTION
    // exactly (and `plusLength` is identical — deliberate sanity row).
    private val VARIANTS: List<Pair<String, ScoringConfig>> = listOf(
        "cleanlinessOnly" to ScoringConfig.PRODUCTION.copy(
            process = ProcessScoreWeights().copy(
                broken = 0.0,
                skipTests = 0.0,
                manualIde = 0.0,
                commitGap = 0.0,
                length = 0.0,
            ),
        ),
        "plusBroken" to ScoringConfig.PRODUCTION.copy(
            process = ProcessScoreWeights().copy(
                skipTests = 0.0,
                manualIde = 0.0,
                commitGap = 0.0,
                length = 0.0,
            ),
        ),
        "plusHygieneTriplet" to ScoringConfig.PRODUCTION.copy(
            process = ProcessScoreWeights().copy(
                length = 0.0,
            ),
        ),
        "plusLength" to ScoringConfig.PRODUCTION.copy(
            process = ProcessScoreWeights().copy(),
        ),
        "full" to ScoringConfig.PRODUCTION,
    )

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
        val fixtureName = fixture.nameWithoutExtension

        val out = mutableListOf<String>()
        for ((name, cfg) in VARIANTS) {
            val perturbedReport = ReportAssembler.assemble(phaseA, cfg)
            val perturbedRanking = ranking(perturbedReport)
            val tau = RankingMetrics.kendallTauB(baselineRanking, perturbedRanking)
            val hit = RankingMetrics.topNHitRate(baselineRanking, perturbedRanking, 5)
            out += listOf(
                fixtureName,
                name,
                String.format(Locale.US, "%.6f", tau),
                String.format(Locale.US, "%.6f", hit),
                baselineRanking.size.toString(),
                perturbedRanking.size.toString(),
            ).joinToString(",")
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
        System.err.println("ablation: $reason")
        System.err.println("usage: ablation --corpus <dir-of-phaseA-jsons> --output <results.csv>")
        exitProcess(2)
    }
}

fun main(args: Array<String>) {
    AblationExperiment.main(args)
}
