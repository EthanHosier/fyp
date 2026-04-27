/**
 * Code-cleanliness composite — a single 0..100 score that summarises the
 * six per-checkpoint code-quality metrics into one figure of merit, with
 * a literature-informed weighting.
 *
 * Surfaced two ways:
 *   - As its own dashboard metric (`cleanliness`), with a hover-card
 *     breakdown showing each sub-metric's weight, normalised value, and
 *     points contributed.
 *   - As an input to the process score's "cleanliness gain" term —
 *     `process-score.ts` consumes the 0..1 scalar from this module.
 *
 * --- Design decisions ---
 *
 *  1. Literature-informed weights, not equal mean. No paper covers this
 *     exact bundle, but the closest references converge on a hierarchy:
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
 *
 *  2. Min-max normalisation across the trajectory's observed range. Self-
 *     tunes per project (a 5-WMC bump on a 0..50 project is significant;
 *     on 0..200 it isn't). Mirrors the existing spike-detector convention
 *     in checkpoint-body.tsx.
 *
 *  3. Re-base when sub-metrics are missing. If a metric is absent at a
 *     checkpoint (or has degenerate range across the trajectory) its
 *     weight is redistributed proportionally over the remaining ones,
 *     so the composite stays on [0, 1]. The breakdown carries a
 *     `rebased` flag so the UI can flag that the score isn't a full
 *     six-axis sweep.
 */

import type {
  CheckpointVM,
  CleanlinessBreakdown,
  CleanlinessContribution,
  MetricId,
  MetricVM,
} from "./types"

// Literature-informed weights for the cleanliness composite. Sum to 1.0.
// `Partial` because aggregate metrics (`process`, `cleanliness`) are
// also valid MetricIds but intentionally excluded from the composite —
// including them would be self-referential.
const CLEANLINESS_WEIGHTS: Partial<Record<MetricId, number>> = {
  cognitive: 0.25,
  coupling: 0.2,
  duplication: 0.2,
  readability: 0.15,
  smells: 0.15,
  cohesion: 0.05,
}

export type CleanlinessPoint = {
  /** Composite scalar in [0, 1] for arithmetic (e.g. process-score gain).
   *  `null` when no sub-metric is normalisable at this checkpoint. */
  scalar: number | null
  /** Rounded 0..100 score for display. `null` when no signal. */
  score: number | null
  /** Per-sub-metric decomposition. `null` when no signal. */
  breakdown: CleanlinessBreakdown | null
}

/**
 * Per-checkpoint cleanliness composite + breakdown. One entry per
 * checkpoint in input order, mirroring `computeProcessScores`.
 */
export function computeCleanlinessSeries(
  checkpoints: CheckpointVM[],
  metrics: MetricVM[],
): CleanlinessPoint[] {
  if (checkpoints.length === 0) return []
  const metricById = new Map(metrics.map((m) => [m.id, m]))
  const ranges = metricRanges(checkpoints, metrics)
  return checkpoints.map((c) => {
    const r = cleanlinessAt(c, metricById, ranges)
    if (!r) return { scalar: null, score: null, breakdown: null }
    return { scalar: r.total, score: r.displayTotal, breakdown: r.breakdown }
  })
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
 * Cleanliness composite at one checkpoint with its per-sub-metric
 * breakdown. Returns `null` when no metrics are normalisable (all absent
 * or all degenerate).
 *
 * `total` is in [0, 1] for direct use by the process-score formula;
 * `displayTotal` is the rounded 0..100 value the UI renders.
 */
function cleanlinessAt(
  c: CheckpointVM,
  metricById: Map<MetricId, MetricVM>,
  ranges: Map<MetricId, { lo: number; hi: number }>,
): {
  total: number
  displayTotal: number
  breakdown: CleanlinessBreakdown
} | null {
  let weighted = 0
  let totalW = 0
  const contributions: CleanlinessContribution[] = []
  // Track whether any literature-weighted metric was excluded — surfaced
  // as `rebased` so the UI can flag that the score doesn't represent a
  // full sweep across the six axes.
  let excludedAny = false

  for (const [id, weight] of Object.entries(CLEANLINESS_WEIGHTS) as Array<
    [MetricId, number]
  >) {
    const m = metricById.get(id)
    if (!m) {
      excludedAny = true
      continue
    }
    const range = ranges.get(id)
    if (!range || range.hi === range.lo) {
      excludedAny = true
      continue
    }
    const raw = c.values[id]
    if (typeof raw !== "number") {
      excludedAny = true
      continue
    }
    let n = (raw - range.lo) / (range.hi - range.lo)
    if (m.better === "lower") n = 1 - n
    weighted += weight * n
    totalW += weight
    contributions.push({
      id,
      label: m.label,
      weight,
      normalised: n,
      raw,
      points: 0,
    })
  }
  if (totalW === 0) return null

  const total = weighted / totalW
  // Re-base the per-row points so `Σ points = total · 100`.
  for (const contrib of contributions) {
    contrib.points = (contrib.weight / totalW) * contrib.normalised * 100
  }

  const displayTotal = Math.round(total * 100)
  return {
    total,
    displayTotal,
    breakdown: {
      total: displayTotal,
      contributions,
      rebased: excludedAny,
    },
  }
}
