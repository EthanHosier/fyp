/**
 * Process score — single 0..100 prefix-cumulative trajectory metric.
 *
 * Answers, at each checkpoint t: "is this refactoring trajectory producing
 * cleaner code safely and efficiently, up to here?". Distinct from the six
 * code-quality metrics, which describe state at t; this describes the
 * *process* taken to get there. Decomposable so the detail panel can show
 * which terms moved the score.
 *
 * The cleanliness composite + per-sub-metric breakdown live in
 * `cleanliness.ts` — that module owns the literature-weighted blend, the
 * min-max normalisation, and the per-checkpoint scalar. This file just
 * consumes the scalar to compute the cleanliness-gain term.
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
 *  7. No churn term. Bigger refactorings shouldn't be inherently worse
 *     than smaller ones.
 */

import { computeCleanlinessSeries } from "./cleanliness"
import type {
  CheckpointVM,
  MetricVM,
  ProcessScoreBreakdown,
  ProcessScoreContribution,
  ProcessScoreResult,
  RefactoringStepVM,
} from "./types"

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

  // Cleanliness scalars per checkpoint — owned by cleanliness.ts. We
  // only consume the 0..1 scalar to compute the gain term; the rounded
  // display score and the breakdown are surfaced separately by the
  // view-model for the standalone cleanliness metric.
  const cleanlinessSeries = computeCleanlinessSeries(checkpoints, metrics)
  const cleanliness0 = cleanlinessSeries[0]?.scalar ?? null

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

    // Cleanliness gain — the scalar at t minus the scalar at c0. Both
    // come from cleanliness.ts; null at either end yields 0 gain.
    const cleanT = cleanlinessSeries[t]?.scalar ?? null
    const cleanlinessGain =
      cleanT === null || cleanliness0 === null ? 0 : cleanT - cleanliness0

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
  const clampedValue = Math.max(0, Math.min(100, unclamped))
  // Round at compute-time so every consumer (chart hover, tile, breakdown
  // card) shows the same integer score without each having to remember
  // to format. The contributions stay raw so the breakdown still
  // explains where the points came from at higher precision.
  const total = Math.round(clampedValue)
  const clamped = unclamped !== clampedValue

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
