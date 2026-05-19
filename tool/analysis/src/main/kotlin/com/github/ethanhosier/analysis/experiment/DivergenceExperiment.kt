package com.github.ethanhosier.analysis.experiment

import com.github.ethanhosier.analysis.advice.TrajectoryAdvisor
import com.github.ethanhosier.analysis.experiment.baselines.Baselines
import com.github.ethanhosier.analysis.metrics.derived.ScoringConfig
import com.github.ethanhosier.analysis.pipeline.AnalysisReport
import com.github.ethanhosier.analysis.pipeline.DivergenceKind
import com.github.ethanhosier.analysis.pipeline.PhaseAResult
import com.github.ethanhosier.analysis.pipeline.ReportAssembler
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.system.exitProcess

/**
 * Phase-2.4 divergence-detection driver. Reads a manifest of recorded
 * sessions, each labelled with the **set of [DivergenceKind] values a
 * perfect detector would surface an alternative path for**, and scores
 * the assembled report against that ground truth.
 *
 * ## Methodology — multi-label per-kind confusion matrix
 *
 * For each session and each kind in [DivergenceKind] the experiment
 * emits one of four classifications:
 *
 *  - **TP** — kind ∈ `expected_kinds` AND a divergence point of that
 *    kind fired.
 *  - **FN** — kind ∈ `expected_kinds` AND no DP of that kind fired
 *    (the detector missed a path it should have surfaced).
 *  - **FP** — kind ∉ `expected_kinds` AND a DP of that kind fired
 *    (the detector surfaced a path that doesn't exist in principle).
 *  - **TN** — kind ∉ `expected_kinds` AND no DP of that kind fired.
 *
 * `expected_kinds` is *what a perfect system would be able to flag as
 * alternative paths* — regardless of whether those alts score better,
 * worse, or the same as the user's trajectory. It encodes "alts exist
 * in principle here," not "the alt would be a useful recommendation."
 *
 * Magnitude is reported **alongside** the classification, not folded
 * into it. For each kind we emit the maximum observed magnitude across
 * DPs of that kind (or empty if none fired). Downstream aggregation
 * can then answer the orthogonal question "of the alts the detector
 * surfaced, how many were genuine improvements (magnitude > 0)?" —
 * which the methodology chapter treats as a separate axis from
 * classification correctness.
 *
 * With four kinds × N sessions, the corpus produces 4N classification
 * cells. Per-kind precision/recall fall out as standard:
 *
 * ```
 * precision(k) = TP(k) / (TP(k) + FP(k))
 * recall(k)    = TP(k) / (TP(k) + FN(k))
 * ```
 *
 * ## How to use
 *
 * ```
 * ./gradlew :analysis:divergence \
 *     --args="--corpus <dir-of-phaseA-jsons> \
 *             --manifest <manifest.csv> \
 *             --output <results.csv>"
 * ```
 *
 * Manifest schema (CSV with header, lookup by column name):
 *
 * ```
 * session_id,pattern,strength,target_step,expected_kinds,fixture_path,control_session_id
 * ```
 *
 *  - `session_id` — basename (no `.json`) of the Phase-A file in `--corpus`.
 *  - `pattern` — human label; not consumed by scoring, written through to output.
 *  - `strength` — `loud` / `borderline` / `control`. Descriptive only —
 *     stratifies recall numbers in downstream aggregation. **Not used for the
 *     confusion matrix**; precision is read off the FP column directly
 *     across all sessions.
 *  - `target_step` — integer; user step index where the bad pattern lives.
 *    Informational (step-anchoring disabled — see Things to note).
 *  - `expected_kinds` — **semicolon-separated** set of [DivergenceKind]
 *    values. Empty string = empty set (control sessions, no kind expected).
 *    Order is irrelevant; duplicates are collapsed.
 *  - `fixture_path` — informational only.
 *  - `control_session_id` — optional; basename of a paired un-injected session.
 *
 * Output columns are listed in [HEADER].
 *
 * ## How to interpret
 *
 *  - **Per-kind classification columns** (`ordering_class`, `ide_replay_class`,
 *    `rework_class`, `hygiene_class`) carry the TP/FP/FN/TN label. Aggregate
 *    across the corpus to get per-kind confusion matrices.
 *  - **Per-kind max magnitude columns** carry the strongest observed
 *    magnitude for DPs of that kind in the session (empty if none).
 *    Use to answer "how often did the detector surface an alt that
 *    genuinely improves on the user (magnitude > 0)?" — independent of
 *    whether the surfacing was a TP or FP.
 *  - **`control_dp_count`** — number of divergence points the detector
 *    surfaced on the paired un-injected control session. **Not a
 *    false-positive count.** A control session is one where no bad
 *    pattern was *deliberately injected*, not one where the trajectory
 *    is optimal. Clean recordings can contain genuine suboptimalities
 *    that the detector correctly surfaces; treat this as a
 *    background-DP-density baseline.
 *  - **Baselines**: `endpoint_improved` and `perstep_hit` provide
 *    weaker reference detectors. The full detector should beat them on
 *    the per-kind precision/recall when the manifest is fully labelled.
 *
 * ## Things to note
 *
 *  - **Step-index anchoring is disabled.** The recorded `step_index` of
 *    a DP depends on plugin checkpoint cadence (EDIT_BURST flushes,
 *    in-place rename template artefacts), so the same logical bad step
 *    can land at different indices across recordings. Until checkpoint
 *    placement stabilises, the experiment treats any DP of the right
 *    kind anywhere in the session as evidence — see Tier 0 note in the
 *    git history for context. Re-introduce `target_step ± 1` anchoring
 *    once cadence is reproducible.
 *  - **No magnitude floor in the builder** (b03a7ba). Every synthesised
 *    alt produces a DP regardless of magnitude. Classification doesn't
 *    care about magnitude; the orthogonal magnitude columns let the
 *    methodology chapter apply any threshold post-hoc.
 *  - **COMMIT_GAP DPs have structurally zero magnitude** (cadence isn't
 *    in the score formula yet). A HYGIENE TP can therefore have
 *    `hygiene_max_magnitude = 0` — the classification is still a hit,
 *    the magnitude reading is honestly null on this kind.
 *  - **REWORK magnitudes are reverted-line counts**, weight-independent.
 *    Larger reverts → larger magnitudes; not directly comparable to the
 *    other kinds' process-score deltas.
 *  - **`control_dp_count = -1`** is the sentinel for "no control session
 *    supplied" (not zero — zero is a real value).
 */
object DivergenceExperiment {

    private val readJson = Json { ignoreUnknownKeys = true; isLenient = true }

    /** All kinds the experiment classifies against, in column-output order. */
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

    /**
     * Maps the manifest's `pattern` cell to the [DivergenceKind] of the
     * deliberately injected bad behaviour. Used for the
     * `injection_caught` / `injection_max_magnitude` headline columns:
     * "for the specific bad thing this recording is designed to test,
     * did the detector both surface an alt of the matching kind AND
     * propose one with positive magnitude (a genuine improvement over
     * the user's trajectory)?"
     *
     * `null` means the session has no injection (controls, sanity).
     */
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

    /** Columns expected in the manifest CSV, in header order. */
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

    /** Per-kind classification cell. */
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

        // Headline injection columns. The pattern column maps to a
        // DivergenceKind (or null for controls); `injection_caught` is
        // true when that kind surfaced AND its max magnitude is strictly
        // positive — i.e. the detector found the injected bad behaviour
        // AND proposed an alt that genuinely beats the user. We keep
        // `injection` as the kind name (or empty for controls) so
        // downstream aggregation can stratify by injected kind without
        // re-parsing `pattern`.
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

        // Injection prominence.
        // Sort DPs across all kinds by magnitude (desc) to compute the
        // rank of the highest-magnitude DP of the injection's kind. Stable
        // sort — original ordering breaks ties.
        val injectionDpCount = injectionKind?.let { dpsByKind[it]?.size ?: 0 } ?: 0
        val injectionDpRank: Int? = if (injectionKind == null || injectionDpCount == 0) {
            null
        } else {
            val sortedByMag = report.divergencePoints.sortedByDescending { it.magnitude }
            // 1-indexed position of the first DP of the injection's kind.
            sortedByMag.indexOfFirst { it.kind == injectionKind } + 1
        }
        // injection_step_match: does any DP of the injection's kind have
        // `target_step` either equal to its `stepIndex` (single-step kinds)
        // or contained in its `orderingWindowSteps` (ORDERING)?
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

    /**
     * Tiny purpose-built CSV reader — not worth pulling in a dep for a
     * 7-column flat file we own. No quoting, no embedded commas, no
     * escaping; that's a precondition on the manifest, not a feature of
     * the parser. Crashes loudly on malformed rows.
     *
     * `expected_kinds` is split on `;` (semicolons), trimmed, parsed
     * via [DivergenceKind.valueOf]. Empty string = empty set.
     */
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
