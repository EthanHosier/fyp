/**
 * Process score — single 0..100 prefix-cumulative trajectory metric.
 *
 * Answers, at each checkpoint t: "is this refactoring trajectory producing
 * cleaner code safely and efficiently, up to here?". Distinct from the six
 * code-quality metrics, which describe state at t; this describes the
 * *process* taken to get there. Decomposable so the detail panel can show
 * which terms moved the score.
 *
 * --- Design decisions ---
 *
 *  1. Prefix-cumulative, not point-in-time. Score(t) judges c0..c_t as a
 *     trajectory, not c_t in isolation. That's what makes it a process
 *     score rather than a quality score.
 *
 *  2. Anchor at 50, not 100. A trajectory that doesn't improve and doesn't
 *     damage anything sits mid-range; pure gain and pure damage are both
 *     visible.
 *
 *  3. Frame as process quality, not absolute code quality. Meaningful
 *     between checkpoints / sessions / user-vs-IDE alts — not as an
 *     absolute "73/100".
 *
 *  4. Bounded (rate-based) penalties with asymmetric Laplace smoothing.
 *     All penalties are fractions in [0, 1] so weights map directly to
 *     point costs ("worst-case manual-IDE costs 8 points"). The user's
 *     "don't penalise thinking" rule means long thoughtful sessions
 *     shouldn't accumulate penalties just for being long; rates decouple
 *     penalty magnitude from session length. Laplace `(bad+1)/(total+2)`
 *     on the refactoring-step rates avoids the cliff where 1/1 pins to
 *     100%, but is *only* applied when at least one bad step has
 *     occurred — a perfect record (0 bad of N) returns a 0 penalty so
 *     doing the right thing every time isn't punished.
 *
 *  5. No time term. Per user constraint. The "stayed broken" intuition is
 *     captured by the count of broken checkpoints, not their duration.
 *
 *  6. Net smells, no separate carry penalty. A `carried` smell is already
 *     on the cumulative ledger from when it was `added`; adding a carry
 *     drag would double-count.
 *
 *  7. Cleanliness composite uses literature-informed weights, not equal
 *     mean. No paper covers this exact bundle, but the closest references
 *     converge on a hierarchy:
 *       cognitive   0.25 — Campbell 2018 (Sonar): strongest empirical
 *                          correlate with comprehension time
 *       coupling    0.20 — Chidamber & Kemerer 1994: coupling repeatedly
 *                          the strongest defect predictor in OO studies
 *       duplication 0.20 — Heitlager et al. 2007 (SIG): one of four
 *                          equal pillars in the maintainability model
 *       readability 0.15 — Buse & Weimer 2010: validated readability
 *                          metric, but smaller effect than structural ones
 *       smells      0.15 — symptom of the above; weighting it as primary
 *                          would risk double-counting
 *       cohesion    0.05 — LCOM family is noisy and contested
 *                          (Etzkorn, Counsell)
 *     When a metric is missing at a checkpoint (or has degenerate range
 *     across the trajectory) its weight is redistributed proportionally
 *     over the remaining metrics so the composite stays on [0, 1].
 *
 *  8. Min-max normalisation across the trajectory's observed range. Self-
 *     tunes per project (a 5-WMC bump on a 0..50 project is significant;
 *     on 0..200 it isn't). Mirrors the existing spike-detector convention
 *     in checkpoint-body.tsx.
 *
 *  9. No churn term. Bigger refactorings shouldn't be inherently worse
 *     than smaller ones.
 */

import type {
  CheckpointVM,
  MetricId,
  MetricVM,
  ProcessScoreBreakdown,
  ProcessScoreContribution,
  ProcessScoreResult,
  RefactoringStepVM,
} from "./types"

// Literature-informed weights for the cleanliness composite. Sum to 1.0.
// See design decision 7. Tunable: change here, no other call sites need
// touching — `cleanlinessAt` rebases dynamically when metrics are absent.
// `Partial` because `process` is itself a MetricId but intentionally
// excluded — including it in its own composite would be self-referential.
const CLEANLINESS_WEIGHTS: Partial<Record<MetricId, number>> = {
  cognitive: 0.25,
  coupling: 0.2,
  duplication: 0.2,
  readability: 0.15,
  smells: 0.15,
  cohesion: 0.05,
}

// Top-level term weights. Sum doesn't have to be 100 — the score is
// 50 + gain·35 - Σ penalty·weight, then clamped to [0, 100]. Worst-case
// total penalty is 53, so bottoming out at 0 requires both maximum
// damage AND zero gain — intentional.
const W_GAIN = 35
const W_BROKEN = 20
const W_SMELL = 15
const W_SKIP_TESTS = 10
const W_MANUAL_IDE = 8

const BASELINE = 50

export function computeProcessScores(
  checkpoints: CheckpointVM[],
  refactoringSteps: RefactoringStepVM[],
  metrics: MetricVM[],
): ProcessScoreResult[] {
  if (checkpoints.length === 0) return []

  // Index metrics by id for O(1) lookup of `better` direction. The
  // CLEANLINESS_WEIGHTS map covers all known MetricIds; we still respect
  // whatever subset is in the metrics array (e.g. if a future config
  // disables one).
  const metricById = new Map(metrics.map((m) => [m.id, m]))
  const ranges = metricRanges(checkpoints, metrics)

  // Refactoring steps grouped by their landing checkpoint index so the
  // per-checkpoint loop is O(1) per step. A step lands "at or before"
  // checkpoint t when step.checkpointIndex <= t — we accumulate counters
  // as we walk forward.
  const stepsByCheckpoint = new Map<number, RefactoringStepVM[]>()
  for (const s of refactoringSteps) {
    const list = stepsByCheckpoint.get(s.checkpointIndex) ?? []
    list.push(s)
    stepsByCheckpoint.set(s.checkpointIndex, list)
  }

  const cleanliness0 = cleanlinessAt(checkpoints[0], metricById, ranges)

  // Running totals for prefix accumulation. Smell weights are computed
  // from the CodeSmellVM buckets already on each checkpoint.
  let brokenCount = 0
  let addedSmellWeight = 0
  let resolvedSmellWeight = 0
  let totalSmellWeightSeen = 0
  let refactoringStepsCount = 0
  let testsSkippedCount = 0
  let ideRelevantCount = 0
  let manualWhenIdeCount = 0

  const out: ProcessScoreResult[] = []

  for (let t = 0; t < checkpoints.length; t++) {
    const c = checkpoints[t]

    // Broken-checkpoint count: build OR tests failing.
    if (c.build === "fail" || c.tests === "fail") brokenCount += 1

    // Smell ledger: priority-weighted (priority 1 worst → weight 5,
    // priority 5 least → weight 1). `added` already excludes the seed
    // checkpoint's baseline (view-model.ts buckets seed violations as
    // carried), so we don't double-charge for preexisting smells.
    addedSmellWeight += sumSmellWeight(c.smells.added)
    resolvedSmellWeight += sumSmellWeight(c.smells.resolved)
    totalSmellWeightSeen += sumSmellWeight(c.smells.added)

    // Refactoring-step counters: walk all steps that land at this
    // checkpoint and bump the relevant denominators / numerators.
    const steps = stepsByCheckpoint.get(t) ?? []
    for (const s of steps) {
      refactoringStepsCount += 1
      if (!s.userRanTests) testsSkippedCount += 1
      if (s.ideRelevant) {
        ideRelevantCount += 1
        if (!s.wasPerformedByIde) manualWhenIdeCount += 1
      }
    }

    // Cleanliness composite at t.
    const cleanT = cleanlinessAt(c, metricById, ranges)
    const cleanlinessGain =
      cleanlinessT_minus_0(cleanT, cleanliness0)

    // Penalty fractions, all in [0, 1]. Smoothing kicks in only once
    // there's at least one step / smell observed; before that the
    // penalty is 0 to avoid a phantom mid-rate at session start.
    const brokenFrac = brokenCount / (t + 1)
    const netSmell =
      totalSmellWeightSeen === 0
        ? 0
        : Math.max(0, addedSmellWeight - resolvedSmellWeight) /
          totalSmellWeightSeen
    // Laplace smoothing only when there's at least one bad step. A
    // perfect record (0 bad of N) deserves a 0 penalty — the smoothing
    // was added to soften "1 of 1 = 100%", not to punish doing the
    // right thing every time.
    const skipFrac =
      refactoringStepsCount === 0 || testsSkippedCount === 0
        ? 0
        : (testsSkippedCount + 1) / (refactoringStepsCount + 2)
    const manualFrac =
      ideRelevantCount === 0 || manualWhenIdeCount === 0
        ? 0
        : (manualWhenIdeCount + 1) / (ideRelevantCount + 2)

    const breakdown = buildBreakdown({
      cleanlinessGain,
      brokenFrac,
      brokenCount,
      checkpointsSoFar: t + 1,
      netSmell,
      addedSmellWeight,
      resolvedSmellWeight,
      skipFrac,
      testsSkippedCount,
      refactoringStepsCount,
      manualFrac,
      manualWhenIdeCount,
      ideRelevantCount,
    })

    out.push({ score: breakdown.total, breakdown })
  }

  return out
}

// ---- internals ----

function metricRanges(
  checkpoints: CheckpointVM[],
  metrics: MetricVM[],
): Map<MetricId, { lo: number; hi: number }> {
  const ranges = new Map<MetricId, { lo: number; hi: number }>()
  for (const m of metrics) {
    let lo = Infinity
    let hi = -Infinity
    for (const c of checkpoints) {
      const v = c.values[m.id]
      if (typeof v !== "number") continue
      if (v < lo) lo = v
      if (v > hi) hi = v
    }
    if (Number.isFinite(lo) && Number.isFinite(hi)) {
      ranges.set(m.id, { lo, hi })
    }
  }
  return ranges
}

/**
 * Cleanliness ∈ [0, 1] at one checkpoint, or `null` when no metrics are
 * normalisable (all absent or all degenerate). Caller treats `null` as
 * "no cleanliness signal here" — gain contribution becomes 0.
 */
function cleanlinessAt(
  c: CheckpointVM,
  metricById: Map<MetricId, MetricVM>,
  ranges: Map<MetricId, { lo: number; hi: number }>,
): number | null {
  let weighted = 0
  let totalW = 0
  for (const [id, weight] of Object.entries(CLEANLINESS_WEIGHTS) as Array<
    [MetricId, number]
  >) {
    const m = metricById.get(id)
    if (!m) continue
    const range = ranges.get(id)
    if (!range || range.hi === range.lo) continue
    const raw = c.values[id]
    if (typeof raw !== "number") continue
    let n = (raw - range.lo) / (range.hi - range.lo)
    if (m.better === "lower") n = 1 - n
    weighted += weight * n
    totalW += weight
  }
  if (totalW === 0) return null
  return weighted / totalW
}

function cleanlinessT_minus_0(
  cleanT: number | null,
  clean0: number | null,
): number {
  if (cleanT === null || clean0 === null) return 0
  return cleanT - clean0
}

/** Priority-weighted: PMD priority 1 (worst) → 5, priority 5 → 1. */
function sumSmellWeight(items: { priority: number }[]): number {
  let total = 0
  for (const s of items) {
    const w = 6 - s.priority
    total += w > 0 ? w : 0
  }
  return total
}

function buildBreakdown(args: {
  cleanlinessGain: number
  brokenFrac: number
  brokenCount: number
  checkpointsSoFar: number
  netSmell: number
  addedSmellWeight: number
  resolvedSmellWeight: number
  skipFrac: number
  testsSkippedCount: number
  refactoringStepsCount: number
  manualFrac: number
  manualWhenIdeCount: number
  ideRelevantCount: number
}): ProcessScoreBreakdown {
  const cleanlinessPoints = W_GAIN * args.cleanlinessGain
  const brokenPoints = -W_BROKEN * args.brokenFrac
  const smellPoints = -W_SMELL * args.netSmell
  const skipPoints = -W_SKIP_TESTS * args.skipFrac
  const manualPoints = -W_MANUAL_IDE * args.manualFrac

  const contributions: ProcessScoreContribution[] = [
    {
      id: "cleanliness",
      label: "Cleanliness gain",
      points: cleanlinessPoints,
      detail: formatGainDetail(args.cleanlinessGain),
    },
    {
      id: "broken",
      label: "Broken checkpoints",
      points: brokenPoints,
      detail: `${args.brokenCount} of ${args.checkpointsSoFar} checkpoint${
        args.checkpointsSoFar === 1 ? "" : "s"
      } broken (${pct(args.brokenFrac)})`,
    },
    {
      id: "smells",
      label: "Smells introduced (net)",
      points: smellPoints,
      detail: formatSmellDetail(
        args.addedSmellWeight,
        args.resolvedSmellWeight,
      ),
    },
    {
      id: "skipTests",
      label: "Tests skipped after refactor",
      points: skipPoints,
      detail:
        args.refactoringStepsCount === 0
          ? "no refactorings yet"
          : `${args.testsSkippedCount} of ${args.refactoringStepsCount} refactoring step${
              args.refactoringStepsCount === 1 ? "" : "s"
            } not followed by tests`,
    },
    {
      id: "manualIde",
      label: "Manual when IDE could refactor",
      points: manualPoints,
      detail:
        args.ideRelevantCount === 0
          ? "no IDE-relevant refactorings yet"
          : `${args.manualWhenIdeCount} of ${args.ideRelevantCount} IDE-relevant step${
              args.ideRelevantCount === 1 ? "" : "s"
            } done manually`,
    },
  ]

  const unclamped =
    BASELINE +
    cleanlinessPoints +
    brokenPoints +
    smellPoints +
    skipPoints +
    manualPoints
  const total = Math.max(0, Math.min(100, unclamped))
  const clamped = unclamped !== total

  return { total, baseline: BASELINE, contributions, clamped }
}

function pct(x: number): string {
  return `${Math.round(x * 100)}%`
}

function formatGainDetail(gain: number): string {
  if (gain === 0) return "no change from baseline"
  const dir = gain > 0 ? "up" : "down"
  return `${dir} ${Math.abs(gain).toFixed(2)} from c0 baseline`
}

function formatSmellDetail(added: number, resolved: number): string {
  if (added === 0 && resolved === 0) return "no smells touched"
  if (added === 0) return `${resolved} weight resolved, none introduced`
  if (resolved === 0) return `${added} weight introduced, none resolved`
  return `${added} weight introduced, ${resolved} resolved (net ${added - resolved})`
}
