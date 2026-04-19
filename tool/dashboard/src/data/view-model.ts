import type {
  AnalysisReport,
  CheckpointReport,
  CkClassMetrics,
} from "@/generated/report-types"
import { formatTLabel, shortSha } from "@/lib/format"

import type {
  CheckpointVM,
  DashboardViewModel,
  IntervalVM,
  MetricId,
  MetricVM,
  SessionVM,
  StatusTone,
  TrajectoryVM,
} from "./types"

const METRICS: MetricVM[] = [
  { id: "complexity",  label: "Complexity",  unit: "wmc",   better: "lower", group: "code" },
  { id: "coupling",    label: "Coupling",    unit: "cbo",   better: "lower", group: "code" },
  { id: "duplication", label: "Duplication", unit: "%",     better: "lower", group: "code" },
  { id: "readability", label: "Readability", unit: "chars", better: "lower", group: "code" },
  { id: "churn",       label: "Churn",       unit: "lines", better: "lower", group: "code" },
]

function mean(xs: number[]): number {
  if (xs.length === 0) return 0
  return xs.reduce((a, b) => a + b, 0) / xs.length
}

function round1(n: number): number {
  return Math.round(n * 10) / 10
}

function checkpointValues(c: CheckpointReport): Partial<Record<MetricId, number>> {
  const perClass = c.metrics.ck.perClass
  const values: Partial<Record<MetricId, number>> = {
    complexity: round1(mean(perClass.map((p: CkClassMetrics) => p.wmc))),
    coupling: round1(mean(perClass.map((p: CkClassMetrics) => p.cbo))),
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
  if (ev) return ev.type.toLowerCase().replaceAll("_", " ")
  return shortSha(c.sha)
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

  return { session, metrics: METRICS, checkpoints, intervals, trajectory }
}
