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
        "fixture,variant,active_count,kendall_tau,top5_hit_rate,baseline_size,perturbed_size," +
            "baseline_saturated_dp_count,perturbed_saturated_dp_count," +
            "baseline_total_abs_magnitude,perturbed_total_abs_magnitude," +
            "magnitude_recovery_fraction,mean_abs_magnitude_delta,max_abs_magnitude_delta"

    /**
     * The six process-score weights we ablate over. Each variant is a
     * subset of this list — terms in the subset stay at their production
     * default; terms outside are zeroed. With six knobs the power set
     * has 2^6 = 64 variants, ranging from `(empty)` (every process term
     * stripped — pure baseline noise) to `(gain,broken,skipTests,
     * manualIde,length,commitGap)` (full PRODUCTION).
     */
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

    /** Power-set of [PROCESS_KNOBS] as (variant-name, ScoringConfig) pairs. */
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
            // Pair baseline and perturbed DPs by their position in
            // `divergencePoints` (pre-sort) — DivergencePointBuilder is
            // deterministic from PhaseAResult, so the two lists carry the
            // same DPs in the same order with only `magnitude` changed.
            val perturbedMags = perturbedReport.divergencePoints.map { it.magnitude }
            val perturbedTotalAbs = perturbedMags.sumOf { kotlin.math.abs(it) }
            // If baseline has no magnitude to recover (e.g. a fixture
            // with only COMMIT_GAP DPs whose magnitudes are structurally
            // 0), recovery is undefined. Report 1.0 when perturbed also
            // has zero magnitude (trivially "fully recovered" — the
            // ablation didn't change anything), 0.0 otherwise (a
            // perturbed magnitude appeared where the baseline had none,
            // which would be surprising but possible if a kind-specific
            // builder gate flips).
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

    /**
     * Number of divergence points whose terminal user- or alt-process
     * score is at the [0, 100] clamp boundary. A high count means τ
     * understates the true impact of an ablation — those DPs have
     * weight-invariant magnitudes (clipping eats the change), so the
     * ranking can look stable when it shouldn't.
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
        System.err.println("ablation: $reason")
        System.err.println("usage: ablation --corpus <dir-of-phaseA-jsons> --output <results.csv>")
        exitProcess(2)
    }
}

fun main(args: Array<String>) {
    AblationExperiment.main(args)
}
