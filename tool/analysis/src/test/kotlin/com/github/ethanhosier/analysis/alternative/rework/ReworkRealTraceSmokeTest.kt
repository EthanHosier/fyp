package com.github.ethanhosier.analysis.alternative.rework

import com.github.ethanhosier.analysis.reconstruct.GitRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File
import java.nio.file.Files

@EnabledIfEnvironmentVariable(named = "REWORK_SMOKE_REPO", matches = ".+")
class ReworkRealTraceSmokeTest {

    private val repo = File(System.getenv("REWORK_SMOKE_REPO")!!)

    @Test
    fun `detect and plan over real user trace`() {
        val baseline = git("log", "--all", "--format=%H %s")
            .lines()
            .firstOrNull { it.contains("baseline:") }
            ?.substringBefore(' ')
            ?: error("no baseline commit found")
        val last = git("log", "--all", "--format=%H %s")
            .lines()
            .firstOrNull { it.contains("EDIT_BURST") }
            ?.substringBefore(' ')
            ?: error("no EDIT_BURST commit found")

        val commits = git("log", "--reverse", "--format=%H", "$baseline..$last").lines()
            .filter { it.isNotBlank() }
        println("[smoke] user trace: ${commits.size} commits past baseline $baseline → last $last")

        val stepInputs = mutableListOf<ReworkDetector.StepInput>()
        var prev = baseline
        for ((idx, sha) in commits.withIndex()) {
            val patch = git("diff", "-U0", "-M", prev, sha, "--", "*.java")
            val touched = parseTouchedJavaFiles(patch)
            val preContent = touched.associateWith { showOrEmpty(prev, it) }
            val postContent = touched.associateWith { showOrEmpty(sha, it) }
            stepInputs += ReworkDetector.StepInput(
                stepIndex = idx,
                patch = patch,
                preFileContent = preContent,
                postFileContent = postContent,
            )
            prev = sha
        }

        val pairs = ReworkDetector.detectChunkPairs(stepInputs)
        println("[smoke] chunk pairs: ${pairs.size}")
        for (p in pairs) {
            val summary = p.contentSummary.take(60).replace("\n", " ⏎ ")
            println("  • ${p.direction} step ${p.originatingStep}→${p.terminalStep} ${p.file} ${p.scopeId}  raw=${p.rawLineCount} norm=${p.normalizedLineCount}: $summary")
        }
        if (pairs.isEmpty()) return

        val plannerSteps = stepInputs.map {
            ReworkAlternativeBuilder.StepInput(it.stepIndex, it.patch)
        }

        for (p in pairs) {
            val builderPair = ReworkAlternativeBuilder.ChunkPair(
                originatingStep = p.originatingStep,
                terminalStep = p.terminalStep,
                file = p.file,
                direction = when (p.direction) {
                    ReworkDetector.Direction.ADD_THEN_REMOVE -> ReworkAlternativeBuilder.Direction.ADD_THEN_REMOVE
                    ReworkDetector.Direction.REMOVE_THEN_ADD -> ReworkAlternativeBuilder.Direction.REMOVE_THEN_ADD
                },
                originatingRunStartLine = p.originatingRunStartLine,
                terminalRunStartLine = p.terminalRunStartLine,
                rawLineCount = p.rawLineCount,
            )
            val plan = ReworkAlternativeBuilder.plan(builderPair, plannerSteps)
            if (plan == null) {
                println("[smoke] plan FAILED for ${p.file} ${p.scopeId}  steps ${p.originatingStep}→${p.terminalStep}")
            } else {
                val nonEmpty = plan.steps.count { it.patch.isNotEmpty() }
                println("[smoke] plan OK: ${plan.steps.size} steps total, $nonEmpty non-empty")
            }
        }
    }

    @Test
    fun `synthesise alts end-to-end against real shadow repo`() {
        // Calls the full synthesiser: detector → planner → applyDirect →
        // commits. Verifies the alt SHAs land in the shadow repo.
        val mappingFile = repo.parentFile.resolve("normalized-events.jsonl")
        if (!mappingFile.exists()) {
            println("[synth-smoke] no normalized-events.jsonl alongside repo — skipping")
            return
        }

        val allCommits = ProcessBuilder("git", "log", "--all", "--format=%H %s")
            .directory(repo).redirectErrorStream(true).start()
            .inputStream.bufferedReader().readText().lines()
        val baseline = allCommits.firstOrNull { it.contains("baseline:") }
            ?.substringBefore(' ') ?: error("no baseline commit found")
        val last = allCommits.firstOrNull { it.contains("EDIT_BURST") }
            ?.substringBefore(' ') ?: error("no EDIT_BURST commit found")
        val rangeShas = ProcessBuilder("git", "log", "--reverse", "--format=%H", "$baseline..$last")
            .directory(repo).redirectErrorStream(true).start()
            .inputStream.bufferedReader().readText()
            .lines().filter { it.isNotBlank() }
        val shas = listOf(baseline) + rangeShas

        val mapping = LinkedHashMap<String, String>()
        for ((i, sha) in shas.withIndex()) mapping["event-$i"] = sha

        val reconstruction = com.github.ethanhosier.analysis.model.ReconstructionResult(
            repoDir = repo.toPath(),
            eventCommits = com.github.ethanhosier.analysis.reconstruct.EventCommitMap(mapping),
        )

        val sessionFolder = Files.createTempDirectory("rework-smoke-session-").toFile()
        try {
            val shadowGit = com.github.ethanhosier.analysis.reconstruct.GitRunner(repo.toPath())
            val stepInputs = mutableListOf<ReworkDetector.StepInput>()
            for (k in 0 until shas.size - 1) {
                val patch = shadowGit.diffPatch(shas[k], shas[k + 1], paths = emptyList(), contextLines = 0)
                val touched = com.github.ethanhosier.analysis.diffs.UnifiedDiffParser.parse(patch).files
                    .mapNotNull { it.newPath ?: it.oldPath }.filter { it.endsWith(".java") }.toSet()
                stepInputs += ReworkDetector.StepInput(
                    stepIndex = k,
                    patch = patch,
                    preFileContent = touched.associateWith { shadowGit.showAtSha(shas[k], it).orEmpty() },
                    postFileContent = touched.associateWith { shadowGit.showAtSha(shas[k + 1], it).orEmpty() },
                )
            }
            val pairs = ReworkDetector.detectChunkPairs(stepInputs)
            for ((idx, pair) in pairs.withIndex()) {
                println("\n========== pair $idx: ${pair.direction} steps ${pair.originatingStep}->${pair.terminalStep} ==========")
                val relevant = stepInputs
                    .filter { it.stepIndex in pair.originatingStep..pair.terminalStep }
                    .map { ReworkAlternativeBuilder.StepInput(it.stepIndex, it.patch) }
                val builderPair = ReworkAlternativeBuilder.ChunkPair(
                    originatingStep = pair.originatingStep,
                    terminalStep = pair.terminalStep,
                    file = pair.file,
                    direction = when (pair.direction) {
                        ReworkDetector.Direction.ADD_THEN_REMOVE -> ReworkAlternativeBuilder.Direction.ADD_THEN_REMOVE
                        ReworkDetector.Direction.REMOVE_THEN_ADD -> ReworkAlternativeBuilder.Direction.REMOVE_THEN_ADD
                    },
                    originatingRunStartLine = pair.originatingRunStartLine,
                    terminalRunStartLine = pair.terminalRunStartLine,
                    rawLineCount = pair.rawLineCount,
                )
                val plan = ReworkAlternativeBuilder.plan(builderPair, relevant)
                if (plan == null) {
                    println("(plan returned null)")
                    continue
                }
                for ((i, planStep) in plan.steps.withIndex()) {
                    val original = relevant[i].patch
                    println("\n--- step ${planStep.stepIndex} ORIGINAL ---")
                    print(original)
                    println("--- step ${planStep.stepIndex} PLANNED ---")
                    print(if (planStep.patch.isEmpty()) "(empty)\n" else planStep.patch)
                }
            }

            println("\n========== byte-level diagnostic (pair 0) ==========")
            val diagPair = pairs.firstOrNull()
            if (diagPair != null) {
                val diagPlan = ReworkAlternativeBuilder.plan(
                    ReworkAlternativeBuilder.ChunkPair(
                        originatingStep = diagPair.originatingStep,
                        terminalStep = diagPair.terminalStep,
                        file = diagPair.file,
                        direction = when (diagPair.direction) {
                            ReworkDetector.Direction.ADD_THEN_REMOVE -> ReworkAlternativeBuilder.Direction.ADD_THEN_REMOVE
                            ReworkDetector.Direction.REMOVE_THEN_ADD -> ReworkAlternativeBuilder.Direction.REMOVE_THEN_ADD
                        },
                        originatingRunStartLine = diagPair.originatingRunStartLine,
                        terminalRunStartLine = diagPair.terminalRunStartLine,
                        rawLineCount = diagPair.rawLineCount,
                    ),
                    stepInputs
                        .filter { it.stepIndex in diagPair.originatingStep..diagPair.terminalStep }
                        .map { ReworkAlternativeBuilder.StepInput(it.stepIndex, it.patch) },
                )

                val diagDir = Files.createTempDirectory("rework-diag-")
                val diagPool = com.github.ethanhosier.analysis.metrics.WorktreePool(
                    repo.toPath(), diagDir, size = 1,
                )
                val fromSha = shas[diagPair.originatingStep]
                val wt = diagPool.borrow(fromSha)
                try {
                    val wtGit = com.github.ethanhosier.analysis.reconstruct.GitRunner(wt)
                    wtGit.setLocalIdentity("diag@x", "diag")
                    fun applyAndCommit(stepIndex: Int, patch: String) {
                        val pf = Files.createTempFile("diag-step$stepIndex-", ".patch")
                        Files.writeString(pf, patch)
                        val r = wtGit.applyDirect(pf)
                        println("  diag apply step $stepIndex: $r")
                        if (r is com.github.ethanhosier.analysis.reconstruct.GitRunner.ApplyResult.Ok) {
                            wtGit.commit("diag step $stepIndex")
                        }
                        Files.deleteIfExists(pf)
                    }
                    val planSteps = diagPlan?.steps.orEmpty()
                    val step0Plan = planSteps.first { it.stepIndex == diagPair.originatingStep }
                    applyAndCommit(step0Plan.stepIndex, step0Plan.patch)
                    val filePath0 = wt.resolve(diagPair.file)
                    val lines0 = Files.readAllLines(filePath0)
                    println("After step 0 only — file has ${lines0.size} lines.")
                    for ((i, line) in lines0.withIndex()) {
                        if (line.isBlank() && line.length > 0) {
                            println("  blank-ish line at ${i + 1}: \"${line.replace(" ", "·")}\" (${line.length} chars)")
                        }
                    }
                    println("Lines 28..34 after step 0:")
                    for (ln in 28..34) {
                        val c = lines0.getOrNull(ln - 1) ?: ""
                        println("  $ln: \"${c.replace(" ", "·")}\" (${c.length} chars)")
                    }

                    // Apply step 1 only
                    for (ps in planSteps) {
                        if (ps.stepIndex == diagPair.originatingStep) continue
                        if (ps.stepIndex >= diagPair.terminalStep) break
                        if (ps.patch.isNotEmpty()) applyAndCommit(ps.stepIndex, ps.patch)
                    }

                    // Read file contents at key lines.
                    val filePath = wt.resolve(diagPair.file)
                    val lines = Files.readAllLines(filePath)
                    fun dump(lineNo: Int) {
                        val content = lines.getOrNull(lineNo - 1) ?: "<missing>"
                        val escaped = content.replace(" ", "·").replace("\t", "⇥")
                        println("  synth-pre-step-2 line $lineNo: \"$escaped\" (${content.length} chars)")
                    }
                    println("File content at boundaries (after step 0 + step 1):")
                    dump(30); dump(31); dump(32); dump(33)
                    dump(101); dump(102); dump(103); dump(104)

                    // Now print step 2's planned hunks' expected line content.
                    val step2Plan = planSteps.firstOrNull { it.stepIndex == diagPair.terminalStep }
                    if (step2Plan != null) {
                        println("\nstep 2 PLANNED hunks (escaped):")
                        for (raw in step2Plan.patch.lines()) {
                            val esc = raw.replace(" ", "·").replace("\t", "⇥")
                            println("  $esc")
                        }
                    }
                } finally {
                    diagPool.release(wt)
                    diagPool.close()
                    diagDir.toFile().deleteRecursively()
                }
            }

            println("\n========== synthesiser apply phase ==========")
            val summary = ReworkSynthesiser().run(reconstruction, sessionFolder.toPath())
            println("[synth-smoke] candidates=${summary.candidates}  synthesised=${summary.synthesised.size}  failed=${summary.failed.size}")
            for (s in summary.synthesised) {
                println("  ✓ ${s.direction} steps ${s.originatingStep}->${s.terminalStep} ${s.scopeId}")
                println("      fromSha=${s.fromSha.take(8)} userToSha=${s.userToSha.take(8)}")
                println("      altShas (${s.altShas.size}): ${s.altShas.joinToString(", ") { it.take(8) }}")
            }
            for ((k, v) in summary.failed) println("  ✗ $k — $v")
        } finally {
            sessionFolder.deleteRecursively()
        }
    }

    private fun parseTouchedJavaFiles(patch: String): Set<String> =
        UnifiedDiffParserAdapter.parse(patch).mapNotNull { it.newPath ?: it.oldPath }
            .filter { it.endsWith(".java") }
            .toSet()

    private fun showOrEmpty(sha: String, file: String): String =
        runCatching { git("show", "$sha:$file") }.getOrDefault("")

    private fun git(vararg args: String): String {
        val p = ProcessBuilder(listOf("git") + args)
            .directory(repo)
            .redirectErrorStream(true)
            .start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out
    }

    // Tiny shim to keep the test self-contained.
    private object UnifiedDiffParserAdapter {
        data class FE(val oldPath: String?, val newPath: String?)
        fun parse(patch: String): List<FE> =
            com.github.ethanhosier.analysis.diffs.UnifiedDiffParser.parse(patch).files
                .map { FE(it.oldPath, it.newPath) }
    }
}
