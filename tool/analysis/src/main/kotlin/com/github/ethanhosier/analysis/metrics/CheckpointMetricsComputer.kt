package com.github.ethanhosier.analysis.metrics

import com.github.ethanhosier.analysis.metrics.ck.CkRunner
import com.github.ethanhosier.analysis.metrics.cpd.CpdRunner
import com.github.ethanhosier.analysis.metrics.gradlebuild.GradleBuildRunner
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.metrics.pmd.PmdRunner
import com.github.ethanhosier.analysis.metrics.readability.ReadabilityRunner
import com.github.ethanhosier.analysis.metrics.tests.GradleTestRunner
import com.github.ethanhosier.analysis.metrics.tests.TestResult
import java.nio.file.Path

/**
 * Pure compute: given a checked-out Java project on disk and the SHA it
 * represents, produce a [CheckpointMetrics]. Has no knowledge of git,
 * worktree pools, or pipeline orchestration — both the in-process executor
 * and (later) the lambda handler invoke this against a project dir they
 * materialise themselves.
 *
 * This is the seam that lets metric computation move off the local machine.
 * Lives in `:analysis` for now; designed to migrate to a `:metrics-core`
 * module without source change.
 */
class CheckpointMetricsComputer(
    private val gradleConfig: GradleConfig = GradleConfig.DEFAULT,
) {
    fun compute(projectDir: Path, sha: String): CheckpointMetrics {
        val ck = CkRunner().run(projectDir)
        val pmd = PmdRunner().run(projectDir)
        val cpd = CpdRunner().run(projectDir)
        val readability = ReadabilityRunner().run(projectDir)
        val build = GradleBuildRunner(
            timeout = gradleConfig.buildTimeout,
            gradleUserHome = gradleConfig.gradleUserHomePath,
        ).run(projectDir)
        // Skip tests when build failed — `./gradlew test` re-runs compileJava
        // and hits the same error, wasting seconds per broken checkpoint
        // without producing any test outcome we didn't already know.
        val tests = if (build.success) {
            GradleTestRunner(
                timeout = gradleConfig.testTimeout,
                gradleUserHome = gradleConfig.gradleUserHomePath,
            ).run(projectDir)
        } else {
            TestResult.skipped("build failed (exit ${build.exitCode})")
        }
        return CheckpointMetrics(sha, ck, pmd, cpd, readability, build, tests)
    }
}
