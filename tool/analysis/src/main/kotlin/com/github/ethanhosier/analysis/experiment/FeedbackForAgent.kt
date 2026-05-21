package com.github.ethanhosier.analysis.experiment

import com.github.ethanhosier.analysis.pipeline.AdviceItem
import com.github.ethanhosier.analysis.pipeline.AdviceSeverity
import com.github.ethanhosier.analysis.pipeline.AnalysisReport
import com.github.ethanhosier.analysis.pipeline.DivergenceKind
import com.github.ethanhosier.analysis.pipeline.DivergencePoint
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Render an [AnalysisReport]'s divergence points as a markdown summary
 * that can be injected into a coding agent's prompt before its next
 * session. Mirrors what a human participant takes away from looking at
 * the dashboard at session end.
 *
 * Output contract:
 *  - Every divergence point in `divergencePoints` is rendered (no top-K
 *    filter). Sorted by descending magnitude so the most impactful
 *    items come first.
 *  - Each entry is a markdown bullet showing kind, magnitude, title,
 *    explanation, and any kind-specific extras (file/scope for REWORK,
 *    replaced refactoringId for IDE_REPLAY, window steps for ORDERING,
 *    sub-kind + stretch length for HYGIENE).
 *
 * Usage (via `./gradlew :analysis:feedbackForAgent --args="--report <path> [--output <path>]"`):
 *   --report  path to an analysis-report.json produced by Phase A.
 *   --output  optional output file. Stdout if absent.
 */
object FeedbackForAgent {

    private val readJson = Json { ignoreUnknownKeys = true; isLenient = true }

    @JvmStatic
    fun main(args: Array<String>) {
        val reportPath = requireArg(args, "--report")?.let(Paths::get)
            ?: failUsage("missing --report")
        val outputPath = requireArg(args, "--output")?.let(Paths::get)

        if (!Files.isRegularFile(reportPath)) {
            System.err.println("feedback-for-agent: --report is not a file: $reportPath")
            exitProcess(2)
        }

        val report = readJson.decodeFromString(
            AnalysisReport.serializer(),
            Files.readString(reportPath),
        )

        val markdown = render(report)

        if (outputPath != null) {
            Files.createDirectories(outputPath.parent ?: Paths.get("."))
            Files.writeString(outputPath, markdown)
            System.err.println(
                "feedback-for-agent: wrote ${report.divergencePoints.size} divergence point(s) to $outputPath"
            )
        } else {
            print(markdown)
        }
    }

    fun render(report: AnalysisReport): String {
        val dps = report.divergencePoints.sortedByDescending { it.magnitude }
        val advice = report.advice
        val sb = StringBuilder()
        sb.append("# Session feedback\n\n")
        sb.append("Session id: `${report.session.sessionId}`. ")
        sb.append("The refactoring-trajectory tool produced **${dps.size}** divergence point(s) ")
        sb.append("and **${advice.size}** trajectory-wide advice item(s) for this session. ")
        sb.append("Each entry below names what the tool saw and what the recommended alternative was. ")
        sb.append("Read them and decide what to do differently in the next session.\n\n")

        if (dps.isEmpty() && advice.isEmpty()) {
            sb.append("_No divergence points or advice fired._\n")
            return sb.toString()
        }

        if (dps.isNotEmpty()) {
            sb.append("## Divergence points (sorted by magnitude, highest first)\n\n")
            dps.forEachIndexed { idx, dp ->
                sb.append("### ${idx + 1}. ${kindLabel(dp.kind)} — magnitude ${formatMagnitude(dp.magnitude)} (step ${dp.stepIndex})\n\n")
                sb.append("**${dp.title.trim()}**\n\n")
                sb.append(dp.explanation.trim()).append("\n\n")

                renderKindExtras(dp, sb)
                sb.append("\n")
            }
        }

        if (advice.isNotEmpty()) {
            sb.append("## Trajectory advice\n\n")
            advice.forEachIndexed { idx, item ->
                sb.append("### ${idx + 1}. ${item.kind} — ${severityLabel(item.severity)}\n\n")
                sb.append("**${item.title.trim()}**\n\n")
                sb.append(item.body.trim()).append("\n\n")
            }
        }

        return sb.toString()
    }

    private fun severityLabel(sev: AdviceSeverity): String = when (sev) {
        AdviceSeverity.INFO -> "info"
        AdviceSeverity.WARNING -> "warning"
        AdviceSeverity.CRITICAL -> "critical"
    }

    private fun renderKindExtras(dp: DivergencePoint, sb: StringBuilder) {
        when (dp.kind) {
            DivergenceKind.IDE_REPLAY -> {
                dp.replacedRefactoringId?.let { sb.append("- Replaced refactoring: `$it`\n") }
            }
            DivergenceKind.REWORK -> {
                dp.originatingStepIndex?.let { sb.append("- Originating step: $it\n") }
                dp.file?.let { sb.append("- File: `$it`\n") }
                dp.scopeLabel?.let { sb.append("- Scope: `$it`\n") }
                dp.reworkLineCount?.let { sb.append("- Reverted lines: $it\n") }
                dp.reworkDirection?.let { sb.append("- Direction: $it\n") }
            }
            DivergenceKind.ORDERING -> {
                dp.orderingWindowSteps?.let { steps ->
                    if (steps.isNotEmpty()) sb.append("- Window steps: ${steps.joinToString(", ")}\n")
                }
            }
            DivergenceKind.HYGIENE -> {
                dp.hygieneSubKind?.let { sb.append("- Sub-kind: $it\n") }
                dp.hygieneStretchLength?.let { sb.append("- Stretch length: $it\n") }
            }
        }
    }

    private fun kindLabel(kind: DivergenceKind): String = when (kind) {
        DivergenceKind.IDE_REPLAY -> "IDE_REPLAY (the IDE could have done this refactoring for you)"
        DivergenceKind.REWORK -> "REWORK (you added and then undid roughly the same edit)"
        DivergenceKind.HYGIENE -> "HYGIENE (process-cadence flag — commit gap or skipped tests)"
        DivergenceKind.ORDERING -> "ORDERING (a different ordering of these refactors would have been cleaner)"
    }

    private fun formatMagnitude(mag: Double): String {
        if (mag == mag.toLong().toDouble()) return mag.toLong().toString()
        return "%.2f".format(mag)
    }

    private fun requireArg(args: Array<String>, name: String): String? {
        val i = args.indexOf(name)
        if (i < 0 || i + 1 >= args.size) return null
        return args[i + 1]
    }

    private fun failUsage(msg: String): Nothing {
        System.err.println("feedback-for-agent: $msg")
        System.err.println("usage: feedbackForAgent --report <analysis-report.json> [--output <markdown>]")
        exitProcess(2)
    }
}

fun main(args: Array<String>) {
    FeedbackForAgent.main(args)
}
