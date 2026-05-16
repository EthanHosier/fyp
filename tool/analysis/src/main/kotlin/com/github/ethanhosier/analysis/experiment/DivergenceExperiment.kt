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
 * Phase-2.4 divergence-detection driver. The main injection-style
 * evaluation: for each row in a manifest of recorded sessions, assembles
 * the corresponding Phase-A dump under [ScoringConfig.PRODUCTION] and
 * asks three questions against the ground-truth `target_step`, each
 * isolating a distinct stage of the pipeline:
 *
 *  - **Tier 0 — Detection.** Did the pipeline classify *anything* as
 *    refactoring-shaped at the target step? Surfaced via a
 *    [com.github.ethanhosier.analysis.miner.model.RefactoringStep]
 *    within ±1 of target with `wasPerformedByIde == false`. A Tier-0
 *    miss is a *capture* failure (the plugin's event stream or the
 *    miner couldn't see the manual edit at all) — no amount of
 *    downstream synthesis or scoring can recover it.
 *  - **Tier 1 — Synthesis.** Did the synthesiser produce an alt of the
 *    expected [DivergenceKind] anchored at `target_step`? A Tier-0 hit
 *    but Tier-1 miss means the pipeline saw the manual edit but Eclipse
 *    JDT (or the matching synth path) couldn't construct the IDE
 *    counterfactual. This is a tool-coverage issue (missing spec
 *    mapper, JDT bug, classpath gap, …), independent of scoring.
 *  - **Tier 2 — Surfacing.** Did the divergence-point builder emit a
 *    DP of the expected kind within `target_step ± 1`? With the
 *    magnitude floor lifted from [com.github.ethanhosier.analysis.divergence.DivergencePointBuilder]
 *    (b03a7ba), Tier 2 is essentially Tier 1 lifted into a user-facing
 *    artefact — the *magnitude* of the DP carries the suggestion-quality
 *    signal as a continuous value rather than a hit/miss boolean.
 *
 * In addition to the tier hits, every Tier-2 DP's `magnitude` is
 * recorded (process-score delta `alt − user`; signed). `tier2_alt_better`
 * is `magnitude > 0`. This lets the methodology chapter define
 * thresholds post-hoc rather than baking them into the tool: e.g.
 * "Tier-2 surfacing recall at magnitude ≥ 3" or "fraction of loud
 * IDE_REPLAY sessions where `alt_better` was true."
 *
 * Each row also runs the three baselines for side-by-side comparison.
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
 * session_id,pattern,strength,target_step,expected_kind,fixture_path,control_session_id
 * ```
 *
 *  - `session_id` — basename (no `.json`) of the Phase-A file in `--corpus`.
 *  - `pattern` — human label; not consumed by scoring, written through to output.
 *  - `strength` — `loud` / `borderline` / [STRENGTH_SUBTHRESHOLD].
 *  - `target_step` — integer; user step index where the bad pattern lives.
 *  - `expected_kind` — must match a [DivergenceKind] value verbatim.
 *  - `fixture_path` — informational only.
 *  - `control_session_id` — optional; basename of a paired un-injected session.
 *
 * Output columns are listed in [HEADER]. Booleans are lowercase
 * `true`/`false`; `advisor_hit` is `NA` (see Things to note).
 *
 * ## How to interpret
 *
 * Per-kind aggregation is the right granularity (see Things to note).
 * Within each kind, three stage-specific recalls peel apart different
 * failure modes:
 *
 *  - **Tier 0 recall** — capture coverage. Should be ≈ 100 % on loud
 *    rows of every pattern; misses here flag plugin event-capture bugs.
 *  - **Tier 1 | Tier 0 (synthesis success rate)** — given the pipeline
 *    saw the manual edit, how often did Eclipse JDT successfully
 *    construct the counterfactual? Failures are tool-coverage gaps
 *    (missing RefactoringSpec mapper, JDT classpath issues, …).
 *  - **Tier 2 | Tier 1 (surfacing rate)** — given the alt was
 *    synthesised, how often did the builder emit a DP for it? With the
 *    magnitude floor lifted, this is now 100 % by construction except
 *    for kind-specific gates (REWORK below `MIN_REWORK_LINES`). The
 *    *interesting* signal at this stage is the **magnitude
 *    distribution** — `tier2_magnitude` and `tier2_alt_better`. A
 *    well-calibrated detector should have positive magnitudes on
 *    `loud` rows and near-zero on `subthreshold` rows; an analyst can
 *    apply any threshold post-hoc.
 *  - **Tier 1 / Tier 2 precision** must be computed against the
 *    [STRENGTH_SUBTHRESHOLD] rows: on those, a `true` is a false
 *    positive. Aggregate FP rate = subthreshold rows with hits ÷
 *    subthreshold rows total.
 *  - **`control_fp_count`** — number of divergence points the detector
 *    surfaces on the paired un-injected control session. Counts as
 *    pure false positives; complements the subthreshold FP measurement
 *    (subthreshold = "we did *something* small," control = "we did
 *    nothing at all").
 *  - **Baselines**: `endpoint_improved` collapses to a single boolean
 *    per session and so cannot pinpoint a step; it answers "would a
 *    naïve last-vs-first cleanliness Δ have caught this?". A `true`
 *    here on a non-subthreshold row is the headline negative — the
 *    detector should beat it because it can localise the step.
 *    `perstep_hit` checks the weaker "any step where process score
 *    dropped > θ" signal against `target_step ± 1`.
 *
 * ## Things to note
 *
 *  - **No magnitude floor in the builder.** As of b03a7ba, the
 *    `MIN_PROCESS_DELTA` gate was removed from
 *    [com.github.ethanhosier.analysis.divergence.DivergencePointBuilder].
 *    Every synthesised alt now produces a divergence point regardless
 *    of magnitude. Detection/synthesis are decoupled from
 *    suggestion-quality at the tool layer; the experiment recovers
 *    suggestion-quality via `tier2_magnitude` and `tier2_alt_better`.
 *  - **COMMIT_GAP magnitude is 0 by construction** (cadence isn't
 *    in the score formula yet). Tier-2 recall ≈ 100 % for COMMIT_GAP
 *    injections, but `tier2_alt_better` will be `false`. That's the
 *    transparent picture — Tier-2 hit is evidence the synthesiser saw
 *    the gap, not that the weights are calibrated.
 *  - **REWORK magnitude is reverted-line count**, not process-score
 *    delta. Larger reverts → larger magnitudes, but the column's
 *    semantics differ from other kinds — interpret per-kind.
 *  - **IDE_REPLAY / ORDERING / HYGIENE TESTS_SKIPPED are the
 *    weight-calibrated kinds.** Their magnitude IS the process-score
 *    delta. Headline detector-vs-baseline claims should be reported
 *    per-kind and ideally over these three.
 *  - **`advisor_hit` is always `NA` today** — [TrajectoryAdvisor]
 *    returns [com.github.ethanhosier.analysis.pipeline.AdviceItem]s
 *    that carry no step anchor, so we can't compare advisor output
 *    against `target_step`. Widening [com.github.ethanhosier.analysis.pipeline.AdviceItem]
 *    with a step field would make this column scoreable; that's a
 *    follow-up, not a blocker for this experiment.
 *  - **Subthreshold semantics**: `tier1_hit = true` on a subthreshold
 *    row is *bad* (a false positive). We still write the boolean
 *    verbatim; aggregation flips the sign.
 *  - **`control_fp_count = -1`** is the sentinel for "no control
 *    session supplied" (not zero — zero is a real value).
 */
object DivergenceExperiment {

    private val readJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val HEADER =
        "session_id,pattern,strength,target_step,expected_kind," +
            "tier0_hit,tier1_hit,tier2_hit,tier2_magnitude,tier2_alt_better," +
            "endpoint_improved,perstep_hit,advisor_hit," +
            "control_fp_count"

    private const val STRENGTH_SUBTHRESHOLD = "subthreshold"

    /** Columns expected in the manifest CSV, in header order. */
    private val MANIFEST_COLUMNS = listOf(
        "session_id",
        "pattern",
        "strength",
        "target_step",
        "expected_kind",
        "fixture_path",
        "control_session_id",
    )

    private data class ManifestRow(
        val sessionId: String,
        val pattern: String,
        val strength: String,
        val targetStep: Int,
        val expectedKind: DivergenceKind,
        val fixturePath: String,
        val controlSessionId: String?,
    )

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

        // Tier 0 — detection: did the pipeline classify *something* manual
        // (or otherwise refactoring-shaped) at the target step? Surfaced via
        // a RefactoringStep within ±1 with wasPerformedByIde == false.
        val tier0Hit = report.refactoringSteps.any { step ->
            !step.wasPerformedByIde &&
                kotlin.math.abs(step.stepIndex - row.targetStep) <= 1
        }
        // Tier 1 — synthesis: an alt of the expected kind exists at target.
        val tier1Hit = report.alternativeTrajectories.any { alt ->
            alt.kind == row.expectedKind && row.targetStep in alt.stepIndexes
        }
        // Tier 2 — surfacing: a divergence point of expected kind fired
        // within ±1. With the magnitude-floor removed from
        // DivergencePointBuilder, this is essentially Tier 1 lifted into a
        // user-facing artefact; the *magnitude* column below carries the
        // suggestion-quality signal.
        val tier2Dp = report.divergencePoints.firstOrNull { dp ->
            dp.kind == row.expectedKind && kotlin.math.abs(dp.stepIndex - row.targetStep) <= 1
        }
        val tier2Hit = tier2Dp != null
        val tier2Magnitude = tier2Dp?.magnitude
        val tier2AltBetter = tier2Magnitude?.let { it > 0.0 }

        val endpointImproved = Baselines.endpointOnlyImproved(report)
        val perstepHit = Baselines.perStepRegression(report).any {
            kotlin.math.abs(it - row.targetStep) <= 1
        }
        // TODO: TrajectoryAdvisor.advise returns AdviceItem with no step
        // index field — no fair way to anchor it against target_step.
        // Emit NA rather than fabricate a boolean.
        val advisorHit = "NA"
        // Touch the advisor anyway so its production path runs in this
        // experiment (catches regressions to its plumbing).
        TrajectoryAdvisor.advise(report)

        val controlFpCount = row.controlSessionId
            ?.takeIf { it.isNotEmpty() }
            ?.let { computeControlFpCount(corpus, it) }
            ?: -1

        return listOf(
            row.sessionId,
            row.pattern,
            row.strength,
            row.targetStep.toString(),
            row.expectedKind.name,
            tier0Hit.toString(),
            tier1Hit.toString(),
            tier2Hit.toString(),
            tier2Magnitude?.let { String.format(java.util.Locale.US, "%.4f", it) } ?: "",
            tier2AltBetter?.toString() ?: "",
            endpointImproved.toString(),
            perstepHit.toString(),
            advisorHit,
            controlFpCount.toString(),
        ).joinToString(",")
    }

    private fun computeControlFpCount(corpus: Path, controlSessionId: String): Int {
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
            val expectedKind = try {
                DivergenceKind.valueOf(cells[4])
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "manifest row ${i + 2}: expected_kind '${cells[4]}' is not a " +
                        "DivergenceKind (${DivergenceKind.values().joinToString(",") { it.name }})",
                    e,
                )
            }
            ManifestRow(
                sessionId = cells[0],
                pattern = cells[1],
                strength = cells[2],
                targetStep = cells[3].toIntOrNull()
                    ?: error("manifest row ${i + 2}: target_step not an int: ${cells[3]}"),
                expectedKind = expectedKind,
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

    @Suppress("unused")
    private val SUBTHRESHOLD_DOC = STRENGTH_SUBTHRESHOLD
}

fun main(args: Array<String>) {
    DivergenceExperiment.main(args)
}
