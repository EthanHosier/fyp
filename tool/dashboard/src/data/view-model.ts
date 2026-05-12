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
  CommitMarkerVM,
  DashboardViewModel,
  DuplicationGroupVM,
  DuplicationOccurrenceVM,
  DuplicationsVM,
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
  { id: "process",     label: "Process Score",        unit: "/100",  better: "higher", group: "process", tone: "brand-2" },
  { id: "cleanliness", label: "Code Cleanliness",     unit: "/100",  better: "higher", group: "process", tone: "brand-3" },
  { id: "cognitive",   label: "Cognitive Complexity", unit: "total", better: "lower",  group: "code",    tone: "brand-4" },
  { id: "readability", label: "Readability",          unit: "/100",  better: "higher", group: "code",    tone: "brand-5" },
  { id: "duplication", label: "Duplication",          unit: "%",     better: "lower",  group: "code",    tone: "brand-6" },
  { id: "smells",      label: "Code Smells",          unit: "count", better: "lower",  group: "code",    tone: "brand-7" },
  { id: "coupling",    label: "Coupling",             unit: "cbo",   better: "lower",  group: "code",    tone: "brand-8" },
  { id: "cohesion",    label: "Cohesion",             unit: "tcc",   better: "higher", group: "code",    tone: "brand-9" },
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

function deriveDuplications(
  curr: CheckpointReport,
  prev: CheckpointReport | null,
): DuplicationsVM {
  const duplications = curr.metrics.cpd?.duplications ?? []
  const firstSeen = curr.cpdTracking?.firstSeenAtSha ?? []
  const resolvedSrc = curr.cpdTracking?.resolvedSincePrev ?? []

  const added: DuplicationGroupVM[] = []
  for (let i = 0; i < duplications.length; i++) {
    const dup = duplications[i]
    const seenAt = firstSeen[i] ?? curr.sha
    // Skip the seed checkpoint (no predecessor → everything looks "new"
    // but is really preexisting baseline). Same convention as smells.
    if (prev === null || seenAt !== curr.sha) continue
    added.push({
      identity: dup.identity ?? "",
      tokens: dup.tokens,
      lines: dup.lines,
      occurrences: dup.occurrences.map(toOccurrenceVM),
      state: "new",
      firstSeenAtSha: seenAt,
    })
  }

  const resolved: DuplicationGroupVM[] = resolvedSrc.map((r) => ({
    identity: r.identity,
    tokens: r.tokens,
    lines: r.lines,
    occurrences: r.prevOccurrences.map(toOccurrenceVM),
    state: "resolved",
    firstSeenAtSha: r.firstSeenAtSha,
  }))

  // Larger blocks are more interesting → tokens DESC, then lines DESC,
  // then first occurrence's file+line as a stable tiebreaker.
  const cmp = (a: DuplicationGroupVM, b: DuplicationGroupVM): number =>
    b.tokens - a.tokens ||
    b.lines - a.lines ||
    (a.occurrences[0]?.file ?? "").localeCompare(b.occurrences[0]?.file ?? "") ||
    (a.occurrences[0]?.beginLine ?? 0) - (b.occurrences[0]?.beginLine ?? 0)
  added.sort(cmp)
  resolved.sort(cmp)

  const totalNow = duplications.length
  const totalPrev = prev?.metrics.cpd?.duplications?.length ?? 0
  return { added, resolved, totalNow, totalPrev, delta: totalNow - totalPrev }
}

function toOccurrenceVM(o: {
  file: string
  beginLine: number
  endLine: number
  snippet?: { patch: string } | null
}): DuplicationOccurrenceVM {
  return {
    file: o.file,
    beginLine: o.beginLine,
    endLine: o.endLine,
    snippetPatch: o.snippet?.patch ?? null,
  }
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
      xPos: 0, // populated by the anchor pass below.
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
      duplications: deriveDuplications(c, prev),
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
      // No transition into the synthetic terminator → no duplication churn.
      duplications: {
        added: [],
        resolved: [],
        totalNow: lastReal.duplications.totalNow,
        totalPrev: lastReal.duplications.totalNow,
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

  // ---- chart X coordinate: per-refactoring-step slots ----
  // Each detected refactoring step takes its own integer slot on the X
  // axis, even when multiple steps land on the same checkpoint
  // (RefactoringMiner sometimes detects N refactorings in a single
  // user-edit transition). Slots are ordered by stepIndex (matches the
  // backend's chronological + RM-detection order). Start and End
  // bracket the slot sequence. Sub-edits between two slots are spaced
  // linearly by their `tMs` within the surrounding slot interval so the
  // rhythm of edits is preserved.
  const reportSteps = (report.refactoringSteps ?? [])
    .filter(
      (s) =>
        s.toCheckpointIndex >= 0 && s.toCheckpointIndex < checkpoints.length,
    )
    .slice()
    .sort((a, b) => a.stepIndex - b.stepIndex)
  type Slot = { xPos: number; cpIdx: number; label: string; stepIndex: number | null }
  const slots: Slot[] = []
  if (checkpoints.length > 0) {
    slots.push({ xPos: 0, cpIdx: 0, label: "Start", stepIndex: null })
  }
  reportSteps.forEach((s, i) => {
    slots.push({
      xPos: slots.length,
      cpIdx: s.toCheckpointIndex,
      label: `R${i + 1}`,
      stepIndex: s.stepIndex,
    })
  })
  if (checkpoints.length > 0) {
    const endIdx = checkpoints.length - 1
    // Avoid a degenerate trailing slot when the last checkpoint is also
    // the final step's landing — End sits one slot to the right anyway
    // so the synthetic terminator gets its own visible tick.
    slots.push({ xPos: slots.length, cpIdx: endIdx, label: "End", stepIndex: null })
  }
  // First-slot xPos per checkpoint (used as cp.xPos for cps that any
  // slot points to). Multiple slots on the same cp → cp anchors at the
  // leftmost slot in its cluster; chart-lines + step-dot rendering
  // visit the other slots independently.
  const firstSlotPosByCp = new Map<number, number>()
  // Last-slot xPos per checkpoint — used to advance the interpolation
  // pointer past a shared-cp cluster when computing non-slot cp xPos.
  const lastSlotPosByCp = new Map<number, number>()
  for (const sl of slots) {
    if (!firstSlotPosByCp.has(sl.cpIdx)) firstSlotPosByCp.set(sl.cpIdx, sl.xPos)
    lastSlotPosByCp.set(sl.cpIdx, sl.xPos)
  }

  // Assign xPos to every checkpoint.
  for (let i = 0; i < checkpoints.length; i++) {
    const cp = checkpoints[i]
    const direct = firstSlotPosByCp.get(i)
    if (direct !== undefined) {
      cp.xPos = direct
      continue
    }
    // Non-anchor checkpoint: interpolate by tMs in the cp-index interval
    // between the surrounding slot-anchored cps. We need cp indices
    // (not slot xPos) for interval bounds, then map to slot xPos via
    // last-slot (left bound) and first-slot (right bound).
    let lCpIdx = -1
    let rCpIdx = -1
    for (let j = i - 1; j >= 0; j--) {
      if (firstSlotPosByCp.has(j)) {
        lCpIdx = j
        break
      }
    }
    for (let j = i + 1; j < checkpoints.length; j++) {
      if (firstSlotPosByCp.has(j)) {
        rCpIdx = j
        break
      }
    }
    if (lCpIdx < 0 || rCpIdx < 0) {
      cp.xPos = lCpIdx >= 0
        ? lastSlotPosByCp.get(lCpIdx) ?? 0
        : firstSlotPosByCp.get(rCpIdx) ?? 0
      continue
    }
    const xL = lastSlotPosByCp.get(lCpIdx) ?? 0
    const xR = firstSlotPosByCp.get(rCpIdx) ?? xL
    const tL = checkpoints[lCpIdx].tMs
    const tR = checkpoints[rCpIdx].tMs
    const denom = tR - tL
    const frac = denom > 0 ? (cp.tMs - tL) / denom : 0
    cp.xPos = xL + Math.max(0, Math.min(1, frac)) * (xR - xL)
  }

  // Tick metadata for chart-axes.
  const xAnchors = slots.map((sl) => ({
    xPos: sl.xPos,
    checkpointIndex: sl.cpIdx,
    label: sl.label,
  }))
  // Per-step xPos, parallel to slots[1..R].
  const stepXPosByStepIndex = new Map<number, number>()
  for (const sl of slots) {
    if (sl.stepIndex != null) stepXPosByStepIndex.set(sl.stepIndex, sl.xPos)
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
      // Each step has its own slot xPos, even when multiple steps share a
      // landing checkpoint (RefactoringMiner can detect N refactorings
      // inside a single user-edit transition). Step dots render side by
      // side at the same y (shared cp metrics) instead of overlapping.
      const slotX = stepXPosByStepIndex.get(s.stepIndex)
        ?? checkpoints[s.toCheckpointIndex]?.xPos
        ?? 0
      return {
        index: i,
        checkpointIndex: s.toCheckpointIndex,
        timestamp: s.timestamp,
        tMs: Math.max(0, s.timestamp - startedAt),
        xPos: slotX,
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

  let altCounter = 0
  const alternativeTrajectories: AlternativeTrajectoryVM[] = (
    report.alternativeTrajectories ?? []
  ).flatMap((alt: AlternativeTrajectory) => {
    const fromIdx = checkpointBySha.get(alt.fromSha)
    const toIdx = checkpointBySha.get(alt.userToSha)
    // Drop alts that don't anchor onto user-trace checkpoints — without
    // both endpoints there's nowhere to draw the branch.
    if (fromIdx === undefined || toIdx === undefined) return []
    if (alt.altCheckpoints.length === 0) return []

    // Terminal step's checkpoint is the alt's end-state — use it for
    // the chart's apex value and the detail panel's status / patch.
    // Multi-step alt structure (per-step labels, intermediate values)
    // is exposed via the `steps` array.
    const terminal = alt.altCheckpoints[alt.altCheckpoints.length - 1]
    const altBuild = buildTone(terminal.metrics.build.success)
    const altTests = testsTone(terminal.metrics.tests.success, terminal.metrics.tests.wasSkipped)
    // IDE-driven alts now expand each refactoring into its own
    // altCheckpoint, with an optional trailing residual step when the
    // user made unrelated edits in the same window that 3-way-merged
    // cleanly on top. So `altCheckpoints.length` ≥ `specs.length`;
    // when it's one longer, the trailing entry is the residual.
    const hasResidualStep = alt.altCheckpoints.length === alt.specs.length + 1
    const residual = alt.residual ?? null
    const residualLabel = residual
      ? `Manual cleanup (+${residual.addedLines} / −${residual.deletedLines})`
      : "Manual cleanup"
    const apexLabel = hasResidualStep
      ? residualLabel
      : specLabel(alt.specs[alt.specs.length - 1])
    const terminalBranchRef = alt.branchRefs[alt.branchRefs.length - 1] ?? ""
    // Synthetic per-VM unique key. Must be unique across the whole VM
    // because the chart's hover/select machinery joins by `index` —
    // reorder alts that share the same first stepIndex would otherwise
    // collide and hover-highlight together.
    const idx = altCounter++

    return [
      {
        index: idx,
        fromCheckpointIndex: fromIdx,
        toCheckpointIndex: toIdx,
        label: apexLabel,
        altSha: terminal.sha,
        shortAltSha: shortSha(terminal.sha),
        branchRef: terminalBranchRef,
        altValues: checkpointValues(terminal),
        build: altBuild,
        tests: altTests,
        status: combineStatus(altBuild, altTests),
        altChurn: terminal.diff?.totalChurn ?? 0,
        patch: report.alternativePatches?.[terminal.sha] ?? "",
        steps: alt.altCheckpoints.map((cp, i) => {
          // Synthesise a CheckpointVM-shaped snapshot so the detail
          // panel can reuse the regular CheckpointBody when the user
          // clicks an alt step on the chart. Index = -1 sentinel: the
          // step isn't in vm.checkpoints, so anything that joins by
          // numeric index will silently no-op.
          //
          // For i < specs.length: this is the i-th refactoring in the
          // group, label with the spec. Trailing step (when present)
          // is the residual cleanup — label it as such.
          const isResidualStep = hasResidualStep && i === alt.specs.length
          const altLabel = isResidualStep
            ? residualLabel
            : `${specLabel(alt.specs[i])} (alt #${alt.stepIndexes[i] ?? i})`
          const prevReport = i === 0
            ? (report.checkpoints[fromIdx] ?? null)
            : alt.altCheckpoints[i - 1]
          const altDerived = cp.derivedMetrics
          const altProcessBreakdown = mapProcessBreakdown(altDerived?.process)
          const altCleanlinessBreakdown = mapCleanlinessBreakdown(altDerived?.cleanliness)
          const altBuildTone = buildTone(cp.metrics.build.success)
          const altTestsTone = testsTone(cp.metrics.tests.success, cp.metrics.tests.wasSkipped)
          const cpVm: CheckpointVM = {
            index: -1,
            label: altLabel,
            tLabel: "alt",
            timestamp: 0,
            tMs: 0,
            // Visual x-position: alt step's slot under the user's k-th
            // window step, matching chart-alternative-paths.
            xPos: 0,
            sha: cp.sha,
            shortSha: shortSha(cp.sha),
            description: altLabel,
            values: checkpointValues(cp),
            build: altBuildTone,
            tests: altTestsTone,
            status: combineStatus(altBuildTone, altTestsTone),
            churn: cp.diff?.totalChurn ?? 0,
            patch: report.alternativePatches?.[cp.sha] ?? "",
            smells: deriveSmells(cp, prevReport),
            duplications: deriveDuplications(cp, prevReport),
            processScore: altProcessBreakdown.total,
            processBreakdown: altProcessBreakdown,
            cleanlinessScore: altCleanlinessBreakdown?.total ?? null,
            cleanlinessBreakdown: altCleanlinessBreakdown,
          }
          return {
            altSha: cp.sha,
            shortAltSha: shortSha(cp.sha),
            // Residual step has no corresponding user stepIndex —
            // anchor it to the group's last refactoring index so the
            // chart's join-by-stepIndex falls onto the same window.
            stepIndex:
              alt.stepIndexes[i] ??
              alt.stepIndexes[alt.stepIndexes.length - 1] ??
              0,
            label: isResidualStep ? residualLabel : specLabel(alt.specs[i]),
            branchRef: alt.branchRefs[i] ?? "",
            altValues: checkpointValues(cp),
            build: altBuildTone,
            tests: altTestsTone,
            altChurn: cp.diff?.totalChurn ?? 0,
            patch: report.alternativePatches?.[cp.sha] ?? "",
            cpVm,
          }
        }),
      },
    ]
  })

  const commitMarkers: CommitMarkerVM[] = (report.userGitCommits ?? [])
    .slice()
    .sort((a, b) => a.timestamp - b.timestamp)
    .map((c) => ({
      sha: c.sha,
      shortSha: shortSha(c.sha),
      message: c.message,
      timestamp: c.timestamp,
      xPos: interpolateXPosByTimestamp(c.timestamp, checkpoints),
    }))

  const advice = (report.advice ?? []).map((a) => ({
    kind: a.kind,
    severity: a.severity,
    title: a.title,
    body: a.body,
  }))

  return {
    session,
    metrics: METRICS,
    checkpoints,
    intervals,
    refactoringSteps,
    alternativeTrajectories,
    commitMarkers,
    advice,
    trajectory,
    xAnchors,
  }
}

/**
 * Place a commit on the chart by linearly interpolating its timestamp
 * between the surrounding checkpoint timestamps. Falls back to the
 * left / right endpoint when the commit lands before the first or after
 * the last checkpoint.
 */
function interpolateXPosByTimestamp(
  ts: number,
  checkpoints: CheckpointVM[],
): number {
  if (checkpoints.length === 0) return 0
  if (ts <= checkpoints[0].timestamp) return checkpoints[0].xPos
  const last = checkpoints[checkpoints.length - 1]
  if (ts >= last.timestamp) return last.xPos
  for (let i = 1; i < checkpoints.length; i++) {
    const l = checkpoints[i - 1]
    const r = checkpoints[i]
    if (ts >= l.timestamp && ts <= r.timestamp) {
      const denom = r.timestamp - l.timestamp
      const frac = denom > 0 ? (ts - l.timestamp) / denom : 0
      return l.xPos + frac * (r.xPos - l.xPos)
    }
  }
  return last.xPos
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
