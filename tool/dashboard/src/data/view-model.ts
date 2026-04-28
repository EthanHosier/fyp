/**
 * Adapter: `AnalysisReport` → `DashboardViewModel`. The chart + rail
 * consume a single number per (checkpoint, metric); the backend computes
 * those numbers (and the cleanliness composite + process score) inside
 * `DerivedMetricsRunner` and ships them on each `CheckpointReport.
 * derivedMetrics`. This adapter just shapes them into VM types and
 * exposes the headline scores via `c.values` so the chart's regular
 * metric plumbing can render them.
 *
 * Sub-metric semantics live alongside the runner; see
 * `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/derived/
 * DerivedMetricsRunner.kt` for formula rationale (literature weights for
 * cleanliness, process-score design decisions, etc.).
 *
 * Churn is intentionally not a chartable metric — "fewer lines" isn't a
 * goal, it's only meaningful relative to an alternative path. We still
 * expose `CheckpointVM.churn` / `IntervalVM.churn` for that comparison.
 */
import type {
  AlternativeTrajectory,
  AnalysisReport,
  CheckpointReport,
  Cleanliness,
  ProcessScore,
  RefactoringSpec,
  RefactoringStep,
} from "@/generated/report-types"
import { formatTLabel, shortSha } from "@/lib/format"

import type {
  AlternativeTrajectoryVM,
  CheckpointVM,
  CleanlinessBreakdown,
  CodeSmellVM,
  CodeSmellsVM,
  DashboardViewModel,
  IntervalVM,
  MetricId,
  MetricVM,
  ProcessScoreBreakdown,
  RefactoringStepVM,
  SessionVM,
  StatusTone,
  TrajectoryVM,
} from "./types"

const METRICS: MetricVM[] = [
  // Tones bump forward across the lineup so the new gold (`brand-8`)
  // lands on cohesion (last). Process stays on prime mint.
  { id: "process",     label: "Process Score",        unit: "/100",  better: "higher", group: "process", tone: "brand"   },
  { id: "cleanliness", label: "Code Cleanliness",     unit: "/100",  better: "higher", group: "process", tone: "brand-2" },
  { id: "cognitive",   label: "Cognitive Complexity", unit: "total", better: "lower",  group: "code",    tone: "brand-3" },
  { id: "readability", label: "Readability",          unit: "/100",  better: "higher", group: "code",    tone: "brand-4" },
  { id: "duplication", label: "Duplication",          unit: "%",     better: "lower",  group: "code",    tone: "brand-5" },
  { id: "smells",      label: "Code Smells",          unit: "count", better: "lower",  group: "code",    tone: "brand-6" },
  { id: "coupling",    label: "Coupling",             unit: "cbo",   better: "lower",  group: "code",    tone: "brand-7" },
  { id: "cohesion",    label: "Cohesion",             unit: "tcc",   better: "higher", group: "code",    tone: "brand-8" },
]

/** Placeholder breakdown used during initial checkpoint construction; gets
 *  overwritten after refactoringSteps is built and we run the real
 *  computation. The fields are required on CheckpointVM so we can't omit. */
const PLACEHOLDER_BREAKDOWN: ProcessScoreBreakdown = {
  total: 0,
  baseline: 0,
  contributions: [],
  clamped: false,
}

/**
 * Pulls the six per-checkpoint aggregators (+ cleanliness + process)
 * out of the backend-computed `derivedMetrics` block. Returns a partial
 * map: `cohesion` may legitimately be missing when no class on the
 * checkpoint has a defined TCC. The other metrics are always present —
 * see `DerivedMetricsRunner.aggregate`.
 *
 * `cleanliness` and `process` end up here too so the chart can render
 * them through the same MetricId plumbing as the raw aggregators.
 * Cleanliness is omitted when the backend couldn't normalise (degenerate
 * trajectory) — `null` in `values` reads as "no data" everywhere.
 */
function checkpointValues(c: CheckpointReport): Partial<Record<MetricId, number>> {
  const d = c.derivedMetrics
  if (!d) return {}
  const values: Partial<Record<MetricId, number>> = {
    coupling: d.coupling,
    duplication: d.duplication,
    readability: d.readability,
    cognitive: d.cognitive,
    smells: d.smells,
  }
  if (d.cohesion != null) values.cohesion = d.cohesion
  if (d.cleanliness?.score != null) values.cleanliness = d.cleanliness.score
  if (d.process?.total != null) values.process = d.process.total
  return values
}

/**
 * Re-shape the backend's `ProcessScore` into the VM's
 * `ProcessScoreBreakdown` — same fields, just narrowing the optional
 * codegened types and tightening `id` to the union the breakdown panel
 * uses for icon/label lookup.
 */
function mapProcessBreakdown(p: ProcessScore | undefined): ProcessScoreBreakdown {
  if (!p) return PLACEHOLDER_BREAKDOWN
  return {
    total: p.total ?? 0,
    baseline: p.baseline ?? 50,
    clamped: p.clamped ?? false,
    contributions: (p.contributions ?? []).map((c) => ({
      id: c.id as ProcessScoreBreakdown["contributions"][number]["id"],
      label: c.label,
      points: c.points,
      detail: c.detail,
    })),
  }
}

function mapCleanlinessBreakdown(c: Cleanliness | null | undefined): CleanlinessBreakdown | null {
  if (!c) return null
  return {
    total: c.score,
    rebased: c.rebased,
    contributions: c.contributions.map((row) => ({
      id: row.id as MetricId,
      label: row.label,
      weight: row.weight,
      normalised: row.normalised,
      raw: row.raw,
      points: row.points,
    })),
  }
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

function deriveSmells(curr: CheckpointReport, prev: CheckpointReport | null): CodeSmellsVM {
  const violations = curr.metrics.pmd?.violations ?? []
  const firstSeen = curr.pmdTracking?.firstSeenAtSha ?? []
  const resolvedSrc = curr.pmdTracking?.resolvedSincePrev ?? []

  const added: CodeSmellVM[] = []
  const carried: CodeSmellVM[] = []
  for (let i = 0; i < violations.length; i++) {
    const v = violations[i]
    // Falls back to curr.sha if tracking hasn't been computed (e.g. an
    // older cached report) — better to render than to crash.
    const seenAt = firstSeen[i] ?? curr.sha
    const vm: CodeSmellVM = {
      rule: v.rule,
      ruleSet: v.ruleSet,
      priority: v.priority,
      file: v.file,
      beginLine: v.beginLine,
      endLine: v.endLine,
      message: v.message,
      snippetPatch: v.snippet?.patch ?? null,
      firstSeenAtSha: seenAt,
    }
    // Seed checkpoint: every violation is stamped at curr.sha by the
    // tracker because there's no predecessor to carry forward from.
    // Surfacing them as "new" misrepresents preexisting baseline smells
    // as something the user just introduced — bucket as carried instead.
    if (prev !== null && seenAt === curr.sha) added.push(vm)
    else carried.push(vm)
  }

  const resolved: CodeSmellVM[] = resolvedSrc.map((r) => ({
    rule: r.rule,
    ruleSet: r.ruleSet,
    priority: r.priority,
    file: r.prevFile,
    beginLine: r.prevBeginLine,
    endLine: r.prevEndLine,
    message: r.message,
    snippetPatch: r.snippet?.patch ?? null,
    firstSeenAtSha: r.firstSeenAtSha,
  }))

  // Severe first, then by file path, then by line. Stable across
  // re-renders so the panel doesn't shuffle when the user expands a
  // bucket.
  const cmp = (a: CodeSmellVM, b: CodeSmellVM): number =>
    a.priority - b.priority ||
    a.file.localeCompare(b.file) ||
    a.beginLine - b.beginLine
  added.sort(cmp)
  carried.sort(cmp)
  resolved.sort(cmp)

  const totalNow = violations.length
  const totalPrev = prev?.metrics.pmd?.violations?.length ?? 0
  return { added, carried, resolved, totalNow, totalPrev, delta: totalNow - totalPrev }
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
    const prev = i === 0 ? null : report.checkpoints[i - 1]
    const d = c.derivedMetrics
    const processBreakdown = mapProcessBreakdown(d?.process)
    const cleanlinessBreakdown = mapCleanlinessBreakdown(d?.cleanliness)
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
      smells: deriveSmells(c, prev),
      processScore: processBreakdown.total,
      processBreakdown,
      cleanlinessScore: cleanlinessBreakdown?.total ?? null,
      cleanlinessBreakdown,
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
      // No real transition into the synthetic terminator, so the
      // delta-bound buckets are empty; carried mirrors the last real
      // checkpoint's full set so totals stay consistent.
      smells: {
        added: [],
        carried: [...lastReal.smells.added, ...lastReal.smells.carried],
        resolved: [],
        totalNow: lastReal.smells.totalNow,
        totalPrev: lastReal.smells.totalNow,
        delta: 0,
      },
      // Synthetic terminator just mirrors the last real checkpoint's
      // scores so the chart's last dot doesn't appear to drop to 0.
      processScore: lastReal.processScore,
      processBreakdown: lastReal.processBreakdown,
      cleanlinessScore: lastReal.cleanlinessScore,
      cleanlinessBreakdown: lastReal.cleanlinessBreakdown,
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
