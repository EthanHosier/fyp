package com.github.ethanhosier.analysis.experiment

import com.github.ethanhosier.analysis.advice.TrajectoryAdvisor
import com.github.ethanhosier.analysis.experiment.baselines.Baselines
import com.github.ethanhosier.analysis.metrics.derived.ScoringConfig
import com.github.ethanhosier.analysis.pipeline.AnalysisReport
import com.github.ethanhosier.analysis.pipeline.DivergencePoint
import com.github.ethanhosier.analysis.pipeline.DivergenceKind
import com.github.ethanhosier.analysis.pipeline.PhaseAResult
import com.github.ethanhosier.analysis.pipeline.ReportAssembler
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.system.exitProcess

object DivergenceExperiment {

    private val readJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private val ALL_KINDS = listOf(
        DivergenceKind.ORDERING,
        DivergenceKind.IDE_REPLAY,
        DivergenceKind.REWORK,
        DivergenceKind.HYGIENE,
    )

    private const val HEADER =
        "session_id,pattern,strength,target_step,expected_kinds,observed_kinds," +
            "ordering_class,ide_replay_class,rework_class,hygiene_class," +
            "ordering_max_magnitude,ide_replay_max_magnitude,rework_max_magnitude,hygiene_max_magnitude," +
            "injection,injection_caught,injection_max_magnitude," +
            "endpoint_improved,perstep_hit,advisor_hit,control_dp_count," +
            // === Per-kind richer DP stats ===
            "ordering_dp_count,ide_replay_dp_count,rework_dp_count,hygiene_dp_count," +
            "ordering_alt_count,ide_replay_alt_count,rework_alt_count,hygiene_alt_count," +
            "ordering_min_magnitude,ide_replay_min_magnitude,rework_min_magnitude,hygiene_min_magnitude," +
            "ordering_mean_magnitude,ide_replay_mean_magnitude,rework_mean_magnitude,hygiene_mean_magnitude," +
            "ordering_beats_count,ide_replay_beats_count,rework_beats_count,hygiene_beats_count," +
            "ordering_magnitudes,ide_replay_magnitudes,rework_magnitudes,hygiene_magnitudes," +
            // === Per-session aggregates ===
            "total_dp_count,total_alt_count,best_alt_magnitude,user_final_process_score," +
            "cleanliness_delta,perstep_regression_steps," +
            // === Injection prominence ===
            "injection_dp_count,injection_dp_rank,injection_step_match"

    private val INJECTION_KIND_BY_PATTERN: Map<String, DivergenceKind?> = mapOf(
        "ManualExtractMethod" to DivergenceKind.IDE_REPLAY,
        "ManualRenameMethod" to DivergenceKind.IDE_REPLAY,
        "ManualInlineMethod" to DivergenceKind.IDE_REPLAY,
        "ManualMoveMethod" to DivergenceKind.IDE_REPLAY,
        "ManualExtractVariable" to DivergenceKind.IDE_REPLAY,
        "SuboptimalOrdering" to DivergenceKind.ORDERING,
        "AddThenRevert" to DivergenceKind.REWORK,
        "SkippedTests" to DivergenceKind.HYGIENE,
        "NoCommitStretch" to DivergenceKind.HYGIENE,
        "Control" to null,
    )

    private val MANIFEST_COLUMNS = listOf(
        "session_id",
        "pattern",
        "strength",
        "target_step",
        "expected_kinds",
        "fixture_path",
        "control_session_id",
    )

    private data class ManifestRow(
        val sessionId: String,
        val pattern: String,
        val strength: String,
        val targetStep: Int,
        val expectedKinds: Set<DivergenceKind>,
        val fixturePath: String,
        val controlSessionId: String?,
    )

    private enum class Classification { TP, FP, FN, TN }

    @JvmStatic
    fun main(args: Array<String>) {
        val corpus = requireArg(args, "--corpus")?.let(Paths::get)
            ?: failUsage("missing --corpus")
        val manifestPath = requireArg(args, "--manifest")?.let(Paths::get)
            ?: failUsage("missing --manifest")
        val output = requireArg(args, "--output")?.let(Paths::get)
            ?: failUsage("missing --output")

        if (!Files.isDirectory(corpus)) {
            System.err.println("divergence: --corpus is not a directory: $corpus")
            exitProcess(2)
        }
        if (!Files.isRegularFile(manifestPath)) {
            System.err.println("divergence: --manifest is not a file: $manifestPath")
            exitProcess(2)
        }

        val rows = parseManifest(manifestPath)
        val started = System.currentTimeMillis()
        val csv = mutableListOf<String>()
        csv += HEADER
        var processed = 0
        for (row in rows) {
            val fixture = corpus.resolve("${row.sessionId}.json")
            if (!fixture.exists()) {
                System.err.println(
                    "divergence: skipping ${row.sessionId} — fixture not found at $fixture",
                )
                continue
            }
            csv += scoreRow(row, fixture, corpus)
            processed++
        }
        Files.writeString(output, csv.joinToString("\n", postfix = "\n"))
        val durationMs = System.currentTimeMillis() - started
        println(
            "divergence: wrote $processed rows from ${rows.size} manifest entries " +
                "to ${output.toAbsolutePath()} in ${durationMs}ms",
        )
    }

    private fun scoreRow(row: ManifestRow, fixture: Path, corpus: Path): String {
        val phaseA = readJson.decodeFromString(
            PhaseAResult.serializer(),
            Files.readString(fixture),
        )
        val report = ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)

        val dpsByKind: Map<DivergenceKind, List<com.github.ethanhosier.analysis.pipeline.DivergencePoint>> =
            report.divergencePoints.groupBy { it.kind }
        val altsByKind: Map<DivergenceKind, Int> = report.alternativeTrajectories
            .groupingBy { it.kind }.eachCount()
        val observedKinds: Set<DivergenceKind> = dpsByKind.keys
        val maxMagByKind: Map<DivergenceKind, Double> = dpsByKind
            .mapValues { (_, dps) -> dps.maxOf { it.magnitude } }
        val minMagByKind: Map<DivergenceKind, Double> = dpsByKind
            .mapValues { (_, dps) -> dps.minOf { it.magnitude } }
        val meanMagByKind: Map<DivergenceKind, Double> = dpsByKind
            .mapValues { (_, dps) -> dps.sumOf { it.magnitude } / dps.size }
        val beatsCountByKind: Map<DivergenceKind, Int> = dpsByKind
            .mapValues { (_, dps) -> dps.count { it.magnitude > 0.0 } }
        val magnitudesListByKind: Map<DivergenceKind, String> = dpsByKind
            .mapValues { (_, dps) ->
                dps.joinToString(";") { String.format(java.util.Locale.US, "%.4f", it.magnitude) }
            }

        val classByKind: Map<DivergenceKind, Classification> = ALL_KINDS.associateWith { k ->
            val expected = k in row.expectedKinds
            val observed = k in observedKinds
            when {
                expected && observed -> Classification.TP
                expected && !observed -> Classification.FN
                !expected && observed -> Classification.FP
                else -> Classification.TN
            }
        }

        val endpointImproved = Baselines.endpointOnlyImproved(report)
        val perstepRegressionSteps = Baselines.perStepRegression(report)
        val perstepHit = perstepRegressionSteps.any {
            kotlin.math.abs(it - row.targetStep) <= 1
        }
        val advisorHit = "NA"
        // Touch the advisor so its production path runs in this experiment
        // (catches regressions to its plumbing).
        TrajectoryAdvisor.advise(report)

        val controlDpCount = row.controlSessionId
            ?.takeIf { it.isNotEmpty() }
            ?.let { computeControlDpCount(corpus, it) }
            ?: -1

        require(INJECTION_KIND_BY_PATTERN.containsKey(row.pattern)) {
            "unknown pattern '${row.pattern}' on session ${row.sessionId} — add it to " +
                "INJECTION_KIND_BY_PATTERN or correct the manifest."
        }
        val injectionKind = INJECTION_KIND_BY_PATTERN.getValue(row.pattern)
        val injectionMag = injectionKind?.let { maxMagByKind[it] }
        val injectionCaught = injectionKind != null &&
            injectionKind in observedKinds &&
            (injectionMag ?: Double.NEGATIVE_INFINITY) > 0.0

        // Per-session aggregates.
        val totalDpCount = report.divergencePoints.size
        val totalAltCount = report.alternativeTrajectories.size
        val bestAltMagnitude = report.divergencePoints.maxOfOrNull { it.magnitude }
        val userFinalProcessScore = report.checkpoints.lastOrNull()
            ?.derivedMetrics?.process?.total
        // Cleanliness delta: last - first cleanliness score (0..100). Null
        // when either endpoint is missing (e.g. degenerate session).
        val firstCleanliness = report.checkpoints.firstOrNull()
            ?.derivedMetrics?.cleanliness?.score
        val lastCleanliness = report.checkpoints.lastOrNull()
            ?.derivedMetrics?.cleanliness?.score
        val cleanlinessDelta = if (firstCleanliness != null && lastCleanliness != null) {
            (lastCleanliness - firstCleanliness)
        } else null

        val injectionDpCount = injectionKind?.let { dpsByKind[it]?.size ?: 0 } ?: 0
        val injectionDpRank: Int? = if (injectionKind == null || injectionDpCount == 0) {
            null
        } else {
            val sortedByMag = report.divergencePoints.sortedWith(
                compareByDescending<DivergencePoint> { it.magnitude }
                    .thenBy { it.stepIndex }
            )
            // 1-indexed position of the first DP of the injection's kind.
            sortedByMag.indexOfFirst { it.kind == injectionKind } + 1
        }
        val injectionStepMatch: Boolean? = if (injectionKind == null) null else {
            val dps = dpsByKind[injectionKind].orEmpty()
            if (dps.isEmpty()) false else dps.any { dp ->
                dp.stepIndex == row.targetStep ||
                    (dp.orderingWindowSteps?.contains(row.targetStep) == true)
            }
        }

        return buildList {
            add(row.sessionId)
            add(row.pattern)
            add(row.strength)
            add(row.targetStep.toString())
            add(row.expectedKinds.joinToString(";") { it.name })
            add(observedKinds.joinToString(";") { it.name })
            for (k in ALL_KINDS) add(classByKind.getValue(k).name)
            for (k in ALL_KINDS) add(
                maxMagByKind[k]?.let { String.format(java.util.Locale.US, "%.4f", it) } ?: "",
            )
            add(injectionKind?.name ?: "")
            add(if (injectionKind == null) "" else injectionCaught.toString())
            add(injectionMag?.let { String.format(java.util.Locale.US, "%.4f", it) } ?: "")
            add(endpointImproved.toString())
            add(perstepHit.toString())
            add(advisorHit)
            add(controlDpCount.toString())
            // --- Per-kind richer DP stats ---
            for (k in ALL_KINDS) add((dpsByKind[k]?.size ?: 0).toString())
            for (k in ALL_KINDS) add((altsByKind[k] ?: 0).toString())
            for (k in ALL_KINDS) add(
                minMagByKind[k]?.let { String.format(java.util.Locale.US, "%.4f", it) } ?: "",
            )
            for (k in ALL_KINDS) add(
                meanMagByKind[k]?.let { String.format(java.util.Locale.US, "%.4f", it) } ?: "",
            )
            for (k in ALL_KINDS) add((beatsCountByKind[k] ?: 0).toString())
            // `magnitudes` lists are semicolon-separated; safe alongside
            // comma-separated CSV — no quoting needed.
            for (k in ALL_KINDS) add(magnitudesListByKind[k] ?: "")
            // --- Per-session aggregates ---
            add(totalDpCount.toString())
            add(totalAltCount.toString())
            add(bestAltMagnitude?.let { String.format(java.util.Locale.US, "%.4f", it) } ?: "")
            add(userFinalProcessScore?.toString() ?: "")
            add(cleanlinessDelta?.toString() ?: "")
            add(perstepRegressionSteps.joinToString(";"))
            // --- Injection prominence ---
            add(if (injectionKind == null) "" else injectionDpCount.toString())
            add(injectionDpRank?.toString() ?: "")
            add(injectionStepMatch?.toString() ?: "")
        }.joinToString(",")
    }

    private fun computeControlDpCount(corpus: Path, controlSessionId: String): Int {
        val controlFixture = corpus.resolve("$controlSessionId.json")
        if (!controlFixture.exists()) {
            System.err.println(
                "divergence: control fixture missing — $controlFixture; emitting -1",
            )
            return -1
        }
        val phaseA = readJson.decodeFromString(
            PhaseAResult.serializer(),
            Files.readString(controlFixture),
        )
        val report: AnalysisReport = ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)
        return report.divergencePoints.size
    }

    private fun parseManifest(path: Path): List<ManifestRow> {
        val lines = Files.readAllLines(path).filter { it.isNotBlank() }
        require(lines.isNotEmpty()) { "manifest is empty: $path" }
        val header = lines.first().split(",").map { it.trim() }
        require(header == MANIFEST_COLUMNS) {
            "manifest header mismatch — expected $MANIFEST_COLUMNS, got $header"
        }
        return lines.drop(1).mapIndexed { i, raw ->
            val cells = raw.split(",").map { it.trim() }
            require(cells.size == MANIFEST_COLUMNS.size) {
                "manifest row ${i + 2} has ${cells.size} cells, expected ${MANIFEST_COLUMNS.size}: $raw"
            }
            val expectedKinds: Set<DivergenceKind> = cells[4]
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { kindStr ->
                    try {
                        DivergenceKind.valueOf(kindStr)
                    } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException(
                            "manifest row ${i + 2}: expected_kinds element '$kindStr' is not a " +
                                "DivergenceKind (${DivergenceKind.values().joinToString(",") { it.name }})",
                            e,
                        )
                    }
                }
                .toSet()
            ManifestRow(
                sessionId = cells[0],
                pattern = cells[1],
                strength = cells[2],
                targetStep = cells[3].toIntOrNull()
                    ?: error("manifest row ${i + 2}: target_step not an int: ${cells[3]}"),
                expectedKinds = expectedKinds,
                fixturePath = cells[5],
                controlSessionId = cells[6].ifEmpty { null },
            )
        }
    }

    private fun requireArg(args: Array<String>, flag: String): String? {
        val idx = args.indexOf(flag)
        if (idx < 0 || idx == args.lastIndex) return null
        return args[idx + 1]
    }

    private fun failUsage(reason: String): Nothing {
        System.err.println("divergence: $reason")
        System.err.println(
            "usage: divergence --corpus <dir-of-phaseA-jsons> --manifest <manifest.csv> --output <results.csv>",
        )
        exitProcess(2)
    }
}

fun main(args: Array<String>) {
    DivergenceExperiment.main(args)
}
