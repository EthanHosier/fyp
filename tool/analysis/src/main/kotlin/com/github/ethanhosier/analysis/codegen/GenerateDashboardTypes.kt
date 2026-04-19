package com.github.ethanhosier.analysis.codegen

import com.github.ethanhosier.analysis.metrics.model.AnalysisReport
import dev.adamko.kxstsgen.KxsTsGenerator
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Generates TypeScript type declarations for the analysis report by walking
 * the `@Serializable` descriptor of [AnalysisReport] via `kxs-ts-gen`.
 * Output path is the first CLI argument.
 *
 * Wired as `:analysis:generateDashboardTypes` → writes
 * `dashboard/src/generated/report-types.ts`, which `:ide-plugin:buildDashboard`
 * depends on so Vite always compiles against fresh types.
 */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage: GenerateDashboardTypes <output-file>" }
    val out = Paths.get(args[0])

    val banner =
        """
        |// AUTO-GENERATED — DO NOT EDIT.
        |// Regenerate with: ./gradlew :analysis:generateDashboardTypes
        |// Source: analysis/src/main/kotlin/.../metrics/model/AnalysisReport.kt
        """.trimMargin()

    val body = KxsTsGenerator().generate(AnalysisReport.serializer())
    val content = "$banner\n\n$body\n"

    Files.createDirectories(out.parent)
    Files.writeString(out, content)
    println("Wrote TS types to $out")
}
