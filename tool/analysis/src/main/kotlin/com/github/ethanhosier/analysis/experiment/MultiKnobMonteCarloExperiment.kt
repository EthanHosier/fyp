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
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random
import kotlin.system.exitProcess

object MultiKnobMonteCarloExperiment {

    private val readJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val HEADER =
        "fixture,sample_id,kendall_tau,top1_hit_rate,top3_hit_rate,top5_hit_rate,baseline_size,perturbed_size"

    private const val DEFAULT_SAMPLES = 200
    private const val DEFAULT_SEED = 1L

    @JvmStatic
    fun main(args: Array<String>) {
        val corpus = requireArg(args, "--corpus")?.let(Paths::get)
            ?: failUsage("missing --corpus")
        val output = requireArg(args, "--output")?.let(Paths::get)
            ?: failUsage("missing --output")
        val samples = requireArg(args, "--samples")?.toIntOrNull() ?: DEFAULT_SAMPLES
        val seed = requireArg(args, "--seed")?.toLongOrNull() ?: DEFAULT_SEED

        if (!Files.isDirectory(corpus)) {
            System.err.println("multiKnobMC: --corpus is not a directory: $corpus")
            exitProcess(2)
        }

        val fixtures = Files.list(corpus).use { stream ->
            stream.filter { it.extension == "json" }.sorted().toList()
        }
        if (fixtures.isEmpty()) {
            System.err.println("multiKnobMC: no *.json fixtures in $corpus")
            exitProcess(2)
        }

        val started = System.currentTimeMillis()
        val rows = mutableListOf<String>()
        rows += HEADER
        for ((fixtureIdx, fixture) in fixtures.withIndex()) {
            rows += sweepFixture(fixture, fixtureIdx, samples, seed)
        }
        Files.writeString(output, rows.joinToString("\n", postfix = "\n"))
        val durationMs = System.currentTimeMillis() - started
        println(
            "multiKnobMC: wrote ${rows.size - 1} rows across ${fixtures.size} fixtures " +
                "($samples samples each, seed=$seed) to ${output.toAbsolutePath()} in ${durationMs}ms",
        )
    }

    private fun sweepFixture(
        fixture: Path,
        fixtureIdx: Int,
        samples: Int,
        seed: Long,
    ): List<String> {
        val phaseA = readJson.decodeFromString(
            PhaseAResult.serializer(),
            Files.readString(fixture),
        )
        val baselineReport = ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)
        val baselineRanking = ranking(baselineReport)
        val fixtureName = fixture.nameWithoutExtension

        // Per-fixture RNG seeded deterministically so the same corpus
        // produces a byte-identical CSV across runs.
        val rng = Random(seed + fixtureIdx * 1_000_003L)
        val sigma = ln(2.0)

        val out = mutableListOf<String>()
        for (sampleId in 0 until samples) {
            val perturbedCfg = drawPerturbedConfig(rng, sigma)
            val perturbedReport = ReportAssembler.assemble(phaseA, perturbedCfg)
            val perturbedRanking = ranking(perturbedReport)
            val tau = RankingMetrics.kendallTauB(baselineRanking, perturbedRanking)
            val hit1 = RankingMetrics.topNHitRate(baselineRanking, perturbedRanking, 1)
            val hit3 = RankingMetrics.topNHitRate(baselineRanking, perturbedRanking, 3)
            val hit5 = RankingMetrics.topNHitRate(baselineRanking, perturbedRanking, 5)
            out += listOf(
                fixtureName,
                sampleId.toString(),
                String.format(Locale.US, "%.6f", tau),
                String.format(Locale.US, "%.6f", hit1),
                String.format(Locale.US, "%.6f", hit3),
                String.format(Locale.US, "%.6f", hit5),
                baselineRanking.size.toString(),
                perturbedRanking.size.toString(),
            ).joinToString(",")
        }
        return out
    }

    private fun drawPerturbedConfig(rng: Random, sigma: Double): ScoringConfig {
        fun draw(): Double = exp(sigma * rng.nextGaussian())
        val prod = ScoringConfig.PRODUCTION
        return prod.copy(
            process = ProcessScoreWeights(
                gain = prod.process.gain * draw(),
                broken = prod.process.broken * draw(),
                skipTests = prod.process.skipTests * draw(),
                manualIde = prod.process.manualIde * draw(),
                length = prod.process.length * draw(),
                commitGap = prod.process.commitGap * draw(),
                lag = prod.process.lag * draw(),
            ),
            cleanliness = CleanlinessWeights(
                cognitive = prod.cleanliness.cognitive * draw(),
                coupling = prod.cleanliness.coupling * draw(),
                duplication = prod.cleanliness.duplication * draw(),
                readability = prod.cleanliness.readability * draw(),
                smells = prod.cleanliness.smells * draw(),
                cohesion = prod.cleanliness.cohesion * draw(),
            ),
        )
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

    private fun requireArg(args: Array<String>, flag: String): String? {
        val idx = args.indexOf(flag)
        if (idx < 0 || idx == args.lastIndex) return null
        return args[idx + 1]
    }

    private fun failUsage(msg: String): Nothing {
        System.err.println("multiKnobMC: $msg")
        System.err.println("usage: multiKnobMC --corpus <dir-of-phaseA-jsons> --output <results.csv> [--samples N] [--seed N]")
        exitProcess(2)
    }
}

private fun Random.nextGaussian(): Double {
    // Polar-rejection Marsaglia method — deterministic given the
    // underlying Random's state, so the experiment stays reproducible.
    var u: Double
    var v: Double
    var s: Double
    do {
        u = nextDouble(-1.0, 1.0)
        v = nextDouble(-1.0, 1.0)
        s = u * u + v * v
    } while (s >= 1.0 || s == 0.0)
    val factor = kotlin.math.sqrt(-2.0 * ln(s) / s)
    return u * factor
}

fun main(args: Array<String>) {
    MultiKnobMonteCarloExperiment.main(args)
}
