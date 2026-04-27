/**
 * Adapter: `AnalysisReport` → `DashboardViewModel`. The chart + rail
 * consume a single number per (checkpoint, metric). Each metric below
 * collapses a richer underlying structure:
 *
 *   cognitive   · total  sum of `pmd.methodMetrics[].cognitive` — Sonar's
 *                         cognitive complexity is additive by design, so
 *                         the project-wide sum is the canonical roll-up.
 *                         Replaces WMC / mean-cyclo, which both pessimise
 *                         Extract Method (each new method adds base 1).
 *   cohesion    · tcc    mean of `ck.perClass[].tcc` over classes where
 *                         TCC is defined (≥2 eligible method pairs).
 *                         Higher = better. Distinct axis from cognitive
 *                         and coupling: "are responsibilities tangled?"
 *   duplication · %      `cpd.duplicatedLinesShare`   × 100
 *   readability · /100   composite of 5 sub-signals from
 *                         `readability.summary`: line length, indentation,
 *                         identifier length, single-letter-ratio, dictionary
 *                         word ratio. Each saturates against a literature
 *                         threshold and contributes a weighted share. See
 *                         `readabilityScore`. Higher = more readable.
 *   coupling    · cbo    p90 of `ck.perClass[].cbo`   (tail)
 *   smells      · count  size of `pmd.violations` — total PMD rule
 *                         violations across the project. Untweighted
 *                         count; consider weighting by `priority` later.
 *
 * Churn is intentionally not a chartable metric — "fewer lines" isn't a
 * goal, it's only meaningful relative to an alternative path. We still
 * expose `CheckpointVM.churn` / `IntervalVM.churn` for that comparison.
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
  { id: "cognitive",   label: "Cognitive Complexity", unit: "total", better: "lower",  group: "code", tone: "brand"   },
  { id: "readability", label: "Readability",          unit: "/100",  better: "higher", group: "code", tone: "brand-2" },
  { id: "duplication", label: "Duplication",          unit: "%",     better: "lower",  group: "code", tone: "brand-3" },
  { id: "smells",      label: "Code Smells",          unit: "count", better: "lower",  group: "code", tone: "brand-4" },
  { id: "coupling",    label: "Coupling",             unit: "cbo",   better: "lower",  group: "code", tone: "brand-5" },
  { id: "cohesion",    label: "Cohesion",             unit: "tcc",   better: "higher", group: "code", tone: "brand-6" },
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

function mean(xs: number[]): number {
  if (xs.length === 0) return 0
  return xs.reduce((s, x) => s + x, 0) / xs.length
}

/**
 * Composite readability score in [0, 1], higher = better. Weighted blend
 * of 5 sub-signals from `readability.summary`, each clamped to [0, 1] and
 * weighted to sum to 1. Comment ratio is intentionally excluded — comment
 * density is a poor readability proxy in modern Java.
 */
function readabilityScore(summary: {
  avgLineLength: number
  avgIndentation: number
  avgIdentifierLength: number
  singleLetterRatio: number
  dictionaryWordRatio: number
}): number {
  const lineLengthScore = 1 - Math.min(1, summary.avgLineLength / 100)
  const indentationScore = 1 - Math.min(1, summary.avgIndentation / 12)
  const identifierLengthScore = Math.min(1, summary.avgIdentifierLength / 5)
  const singleLetterScore = 1 - Math.min(1, Math.max(0, summary.singleLetterRatio))
  const dictionaryScore = Math.min(1, Math.max(0, summary.dictionaryWordRatio))
  return (
    0.25 * lineLengthScore +
    0.20 * indentationScore +
    0.20 * identifierLengthScore +
    0.15 * singleLetterScore +
    0.20 * dictionaryScore
  )
}

function checkpointValues(c: CheckpointReport): Partial<Record<MetricId, number>> {
  const perClass = c.metrics.ck.perClass
  const values: Partial<Record<MetricId, number>> = {
    coupling: round1(percentile(perClass.map((p: CkClassMetrics) => p.cbo), 0.9)),
  }
  // CK returns null on classes where TCC is undefined (e.g. < 2 eligible
  // method pairs). Drop those before averaging — counting them as 0
  // would falsely report "no cohesion" for trivial classes.
  const tccs = perClass.map((p) => p.tcc).filter((t): t is number => t != null)
  if (tccs.length > 0) {
    values.cohesion = Math.round(mean(tccs) * 100) / 100
  }
  if (c.metrics.cpd) {
    values.duplication = round1(c.metrics.cpd.duplicatedLinesShare * 100)
  }
  if (c.metrics.readability?.summary) {
    values.readability = round1(readabilityScore(c.metrics.readability.summary) * 100)
  }
  const pmd = c.metrics.pmd
  if (pmd) {
    values.cognitive = pmd.methodMetrics.reduce((s, m) => s + m.cognitive, 0)
    values.smells = pmd.violations.length
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
