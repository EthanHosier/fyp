/**
 * Adapter: `AnalysisReport` → `DashboardViewModel`. The chart + rail
 * consume a single number per (checkpoint, metric). Each metric below
 * collapses a richer underlying structure:
 *
 *   complexity  · wmc    p90 of `ck.perClass[].wmc`   (tail — surfaces hotspots)
 *   coupling    · cbo    p90 of `ck.perClass[].cbo`   (tail)
 *   duplication · %      `cpd.duplicatedLinesShare`   × 100
 *   readability · chars  `readability.summary.avgLineLength` — placeholder
 *                         proxy; real composite (comments, identifiers,
 *                         indentation…) is deferred
 *   churn       · lines  `diff.totalChurn`            (added + deleted)
 *
 * p90 over mean: a single pathological class with wmc=120 is invisible
 * next to 50 classes at wmc=5; the 90th percentile makes it visible
 * without just showing `max` (which is jittery).
 *
 * Still dropped: per-file churn, PMD violations, CPD blocks, per-method
 * cognitive complexity, LOC-weighting. All sit in the raw report and
 * can be folded in here without touching feature code.
 */
import type {
  AnalysisReport,
  CheckpointReport,
  CkClassMetrics,
  RefactoringStep,
} from "@/generated/report-types"
import { formatTLabel, shortSha } from "@/lib/format"

import type {
  CheckpointVM,
  DashboardViewModel,
  IntervalVM,
  MetricId,
  MetricVM,
  RefactoringStepVM,
  SessionVM,
  StatusTone,
  TrajectoryVM,
} from "./types"

const METRICS: MetricVM[] = [
  { id: "complexity",  label: "Complexity",  unit: "wmc",   better: "lower",  group: "code", tone: "brand"   },
  { id: "coupling",    label: "Coupling",    unit: "cbo",   better: "lower",  group: "code", tone: "brand-2" },
  { id: "duplication", label: "Duplication", unit: "%",     better: "lower",  group: "code", tone: "brand-3" },
  { id: "readability", label: "Readability", unit: "chars", better: "lower",  group: "code", tone: "brand-4" },
  { id: "churn",       label: "Churn",       unit: "lines", better: "lower",  group: "code", tone: "brand-5" },
]

function round1(n: number): number {
  return Math.round(n * 10) / 10
}

/** Value at the given percentile (0..1), nearest-rank. */
function percentile(xs: number[], p: number): number {
  if (xs.length === 0) return 0
  const sorted = [...xs].sort((a, b) => a - b)
  const idx = Math.min(sorted.length - 1, Math.floor(p * sorted.length))
  return sorted[idx]
}

function checkpointValues(c: CheckpointReport): Partial<Record<MetricId, number>> {
  const perClass = c.metrics.ck.perClass
  const values: Partial<Record<MetricId, number>> = {
    complexity: round1(percentile(perClass.map((p: CkClassMetrics) => p.wmc), 0.9)),
    coupling: round1(percentile(perClass.map((p: CkClassMetrics) => p.cbo), 0.9)),
  }
  if (c.metrics.cpd) {
    values.duplication = round1(c.metrics.cpd.duplicatedLinesShare * 100)
  }
  if (c.metrics.readability?.summary) {
    values.readability = round1(c.metrics.readability.summary.avgLineLength)
  }
  if (c.diff) {
    values.churn = c.diff.totalChurn
  }
  return values
}

function buildTone(ok: boolean): StatusTone {
  return ok ? "pass" : "fail"
}

function testsTone(success: boolean, skipped: boolean | undefined): StatusTone {
  if (skipped) return "unknown"
  return success ? "pass" : "fail"
}

function combineStatus(a: StatusTone, b: StatusTone): StatusTone {
  if (a === "fail" || b === "fail") return "fail"
  if (a === "unknown" || b === "unknown") return "unknown"
  return "pass"
}

function describeCheckpoint(c: CheckpointReport): string {
  const ev = c.events[0]
  if (!ev) return shortSha(c.sha)
  return eventTypeLabel(ev.type)
}

function eventTypeLabel(type: string): string {
  if (type === "SESSION_STARTED") return "Start"
  if (type === "SESSION_ENDED") return "End"
  if (type === "EDIT_BURST") return "Manual Edit"
  if (type === "REFACTORING_FINISHED" || type === "REFACTORING_STARTED") {
    return "IDE Refactoring"
  }
  return type
    .toLowerCase()
    .split("_")
    .map((s) => (s ? s[0].toUpperCase() + s.slice(1) : s))
    .join(" ")
}

export function toViewModel(report: AnalysisReport): DashboardViewModel {
  const startedAt = report.session.startTime
  const endedAt = report.session.endTime ?? startedAt

  const checkpoints: CheckpointVM[] = report.checkpoints.map((c, i) => {
    const ts = c.events[0]?.timestamp ?? startedAt
    const build = buildTone(c.metrics.build.success)
    const tests = testsTone(c.metrics.tests.success, c.metrics.tests.wasSkipped)
    return {
      index: i,
      label: `c${i}`,
      tLabel: formatTLabel(ts - startedAt),
      timestamp: ts,
      tMs: Math.max(0, ts - startedAt),
      sha: c.sha,
      shortSha: shortSha(c.sha),
      description: describeCheckpoint(c),
      values: checkpointValues(c),
      build,
      tests,
      status: combineStatus(build, tests),
      churn: c.diff?.totalChurn ?? 0,
    }
  })

  const intervals: IntervalVM[] = checkpoints.slice(1).map((to, i) => {
    const from = checkpoints[i]
    const build = combineStatus(from.build, to.build)
    const tests = combineStatus(from.tests, to.tests)
    return {
      index: i,
      from: from.index,
      to: to.index,
      durationMs: Math.max(0, to.timestamp - from.timestamp),
      build,
      tests,
      status: combineStatus(build, tests),
      churn: to.churn,
    }
  })

  const session: SessionVM = {
    name: report.session.name,
    projectName: report.session.projectName,
    branch: report.session.branch ?? null,
    startedAt,
    durationMs: Math.max(0, endedAt - startedAt),
    checkpointCount: checkpoints.length,
    commitHash: report.session.commitHash ?? null,
  }

  const trajectory: TrajectoryVM | undefined = report.trajectory
    ? {
        totalChurn: report.trajectory.totalChurn,
        totalFilesTouched: report.trajectory.totalFilesTouched,
        totalElapsedMs: report.trajectory.totalElapsedMs,
        totalBrokenMs: report.trajectory.totalBrokenMs,
      }
    : undefined

  const refactoringSteps: RefactoringStepVM[] = (report.refactoringSteps ?? []).map(
    (s: RefactoringStep, i) => ({
      index: i,
      checkpointIndex: s.toCheckpointIndex,
      timestamp: s.timestamp,
      tMs: Math.max(0, s.timestamp - startedAt),
      tLabel: formatTLabel(s.timestamp - startedAt),
      refactoringType: s.refactoring.type,
      description: s.refactoring.description,
      fromSha: s.fromSha,
      toSha: s.toSha,
      shortFromSha: shortSha(s.fromSha),
      shortToSha: shortSha(s.toSha),
      ideRelevant: s.refactoring.ideRelevant,
    }),
  )

  return { session, metrics: METRICS, checkpoints, intervals, refactoringSteps, trajectory }
}
