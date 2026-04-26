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
  AlternativeTrajectory,
  AnalysisReport,
  CheckpointReport,
  CkClassMetrics,
  RefactoringSpec,
  RefactoringStep,
} from "@/generated/report-types"
import { formatTLabel, shortSha } from "@/lib/format"

import type {
  AlternativeTrajectoryVM,
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
      patch: report.checkpointPatches?.[c.sha] ?? "",
    }
  })

  // SESSION_ENDED gets merged into the last real checkpoint when no code
  // changes between the last edit and the user hitting stop — so the
  // session's `endTime` is invisible on the chart. Append a synthetic
  // "End" checkpoint at endTime so the trajectory always has a visible
  // terminator. Stats are carried forward from the last checkpoint
  // (there's no diff to compute by definition).
  const lastReal = checkpoints[checkpoints.length - 1]
  if (lastReal && endedAt > lastReal.timestamp) {
    checkpoints.push({
      ...lastReal,
      index: checkpoints.length,
      label: `c${checkpoints.length}`,
      tLabel: formatTLabel(endedAt - startedAt),
      timestamp: endedAt,
      tMs: Math.max(0, endedAt - startedAt),
      description: "End",
      churn: 0,
      patch: "",
    })
  }

  const intervals: IntervalVM[] = checkpoints.slice(1).map((to, i) => {
    const from = checkpoints[i]
    // Edge colour mirrors the LEFT endpoint only — an interval is red
    // iff the checkpoint it's leaving was failing. The right side's
    // status belongs to that checkpoint's own dot.
    return {
      index: i,
      from: from.index,
      to: to.index,
      durationMs: Math.max(0, to.timestamp - from.timestamp),
      build: from.build,
      tests: from.tests,
      status: from.status,
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
    (s: RefactoringStep, i) => {
      const toEvents = report.checkpoints[s.toCheckpointIndex]?.events ?? []
      return {
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
        wasPerformedByIde: s.wasPerformedByIde ?? false,
        userRanTests: toEvents.some((e) => e.type === "TEST_RUN_FINISHED"),
        patch: report.refactoringPatches?.[i] ?? "",
      }
    },
  )

  const checkpointBySha = new Map<string, number>()
  report.checkpoints.forEach((c, i) => checkpointBySha.set(c.sha, i))

  const alternativeTrajectories: AlternativeTrajectoryVM[] = (
    report.alternativeTrajectories ?? []
  ).flatMap((alt: AlternativeTrajectory) => {
    const fromIdx = checkpointBySha.get(alt.fromSha)
    const toIdx = checkpointBySha.get(alt.userToSha)
    // Drop alts that don't anchor onto user-trace checkpoints — without
    // both endpoints there's nowhere to draw the branch.
    if (fromIdx === undefined || toIdx === undefined) return []

    const altC = alt.altCheckpoint
    const altBuild = buildTone(altC.metrics.build.success)
    const altTests = testsTone(altC.metrics.tests.success, altC.metrics.tests.wasSkipped)

    return [
      {
        index: alt.stepIndex,
        fromCheckpointIndex: fromIdx,
        toCheckpointIndex: toIdx,
        label: specLabel(alt.spec),
        altSha: altC.sha,
        shortAltSha: shortSha(altC.sha),
        branchRef: alt.branchRef,
        altValues: checkpointValues(altC),
        build: altBuild,
        tests: altTests,
        status: combineStatus(altBuild, altTests),
        altChurn: altC.diff?.totalChurn ?? 0,
        patch: report.alternativePatches?.[alt.stepIndex] ?? "",
      },
    ]
  })

  return {
    session,
    metrics: METRICS,
    checkpoints,
    intervals,
    refactoringSteps,
    alternativeTrajectories,
    trajectory,
  }
}

/** Human-readable label for the chart's branch chip. The polymorphic
 *  RefactoringSpec discriminator is "type" (kotlinx default) — fall back
 *  to a generic label for unknown variants so a schema bump doesn't blow
 *  up the chart. */
function specLabel(spec: RefactoringSpec): string {
  // The codegen doesn't expose a typed discriminator field; the runtime
  // shape is `{ type: "ExtractMethod", ... }`. Cast through unknown to
  // read it without dragging the whole sealed-shape mirror in here.
  const kind = (spec as unknown as { type?: string }).type
  switch (kind) {
    case "ExtractMethod":
      return "Extract Method"
    case "InlineMethod":
      return "Inline Method"
    case "ExtractVariable":
      return "Extract Variable"
    case "InlineVariable":
      return "Inline Variable"
    case "ExtractAttribute":
      return "Extract Attribute"
    case "ExtractClass":
      return "Extract Class"
    case "ExtractSubclass":
      return "Extract Subclass"
    case "ExtractSuperclass":
      return "Extract Superclass"
    case "ExtractInterface":
      return "Extract Interface"
    case "ExtractAndMoveMethod":
      return "Extract & Move Method"
    case "RenameClass":
      return "Rename Class"
    case "RenameMethod":
      return "Rename Method"
    case "RenameField":
      return "Rename Field"
    case "RenameLocalVariable":
      return "Rename Variable"
    case "RenameParameter":
      return "Rename Parameter"
    case "RenamePackage":
      return "Rename Package"
    case "MoveClass":
      return "Move Class"
    case "MoveAndRenameClass":
      return "Move & Rename Class"
    case "MoveInstanceField":
      return "Move Field"
    case "MoveAndRenameAttribute":
      return "Move & Rename Field"
    case "MoveInstanceMethod":
      return "Move Method"
    case "MoveAndRenameMethod":
      return "Move & Rename Method"
    case "MoveStaticMembers":
      return "Move Static Members"
    case "MovePackage":
      return "Move Package"
    case "PullUp":
      return "Pull Up"
    case "PushDown":
      return "Push Down"
    case "ChangeMethodSignature":
      return "Change Signature"
    case "ChangeVariableType":
      return "Change Variable Type"
    case "ChangeAttributeType":
      return "Change Field Type"
    case "ParameterizeVariable":
      return "Parameterize Variable"
    case "ParameterizeAttribute":
      return "Parameterize Field"
    case "ReplaceVariableWithAttribute":
      return "Variable → Field"
    default:
      return "Refactoring"
  }
}
