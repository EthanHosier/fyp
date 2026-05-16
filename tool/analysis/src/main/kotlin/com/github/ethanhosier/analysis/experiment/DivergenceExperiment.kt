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
 * asks two questions against the ground-truth `target_step`:
 *
 *  - **Tier 1** — did the synthesiser produce an alt of the right
 *    [DivergenceKind] anchored at `target_step`? (Weight-independent —
 *    alts live on [PhaseAResult] and are computed regardless of the
 *    scoring config.)
 *  - **Tier 2** — did [com.github.ethanhosier.analysis.divergence.DivergencePointBuilder]
 *    flag a divergence point of the right kind within `target_step ± 1`?
 *    (Weight-dependent for IDE_REPLAY / ORDERING / HYGIENE TESTS_SKIPPED
 *    via the magnitude floor; weight-independent for REWORK and HYGIENE
 *    COMMIT_GAP.)
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
 * Within each kind:
 *
 *  - **Tier 1 recall** = fraction of rows with `tier1_hit = true`. A
 *    miss here means the synthesiser didn't see the bad pattern at all
 *    — no amount of weight tuning can recover it.
 *  - **Tier 2 recall** = fraction with `tier2_hit = true`. The gap
 *    between Tier 1 and Tier 2 recall is the *surfacing-loss*: alts the
 *    synth saw but the magnitude floor / weight composition filtered
 *    out before they reached the user. This is the metric the
 *    sensitivity / ablation experiments defend.
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
 *  - **COMMIT_GAP is detected unconditionally.** [DivergencePointBuilder]
 *    emits HYGIENE / COMMIT_GAP divergence points without a magnitude
 *    floor (the cadence signal isn't yet in the score formula). So
 *    Tier-2 recall on COMMIT_GAP injections is ~100% by construction
 *    and is **not** evidence of well-calibrated weights — it
 *    demonstrates the synthesiser saw the gap. Per-kind reporting
 *    makes this transparent; aggregate numbers should *not* be used
 *    as the headline.
 *  - **REWORK magnitude is reverted-line count**, weight-independent.
 *    Same disclosure: Tier-2 recall on REWORK is a property of the
 *    REWORK detector, not weight calibration.
 *  - **IDE_REPLAY / ORDERING / HYGIENE TESTS_SKIPPED are the
 *    weight-calibrated kinds.** Headline detector-vs-baseline claims
 *    should be reported per-kind and ideally over these three.
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
            "tier1_hit,tier2_hit,endpoint_improved,perstep_hit,advisor_hit," +
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

        val tier1Hit = report.alternativeTrajectories.any { alt ->
            alt.kind == row.expectedKind && row.targetStep in alt.stepIndexes
        }
        val tier2Hit = report.divergencePoints.any { dp ->
            dp.kind == row.expectedKind && kotlin.math.abs(dp.stepIndex - row.targetStep) <= 1
        }

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
            tier1Hit.toString(),
            tier2Hit.toString(),
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
