import type { MetricId } from "@/data/types"

/**
 * Per-metric copy shown in the chart toolbar's hover-card. `summary`
 * is the short blurb under the chart title; `formula` is the mono
 * one-liner inside the hover panel; `detail` adds the "why this and
 * not X" framing for users curious about the choice.
 */
export type MetricDescriptor = {
  summary: string
  formula: string
  detail: string
}

export const METRIC_DESCRIPTORS: Record<MetricId, MetricDescriptor> = {
  cognitive: {
    summary:
      "How hard the code is to follow at a glance.",
    formula: "Σ (cognitive complexity per method)",
    detail:
      "A high score means readers will struggle to hold a method in their head — usually because of deep nesting, chained conditionals, or jumps that break linear flow. Bringing it down generally means flattening control flow or splitting a long method into smaller, named steps.",
  },
  readability: {
    summary:
      "How approachable the code looks to a new reader.",
    formula:
      "100 × ( 0.25·LineLength + 0.20·Indentation + 0.20·IdentifierLength + 0.15·(1 − SingleLetterRate) + 0.20·DictionaryWordRate )",
    detail:
      "A composite of surface-level cues: shorter lines, shallower indentation, descriptive multi-letter names, and recognisable dictionary words all push the score up. A drop usually points to long lines, dense nesting, or cryptic identifiers worth renaming.",
  },
  duplication: {
    summary:
      "Share of the codebase that is copy-pasted from somewhere else.",
    formula: "( DuplicatedLines / TotalLines ) × 100",
    detail:
      "Duplicated logic means a bug fix has to be applied in several places and it's easy to miss one. A rising trend is a hint to extract the shared piece into a helper, base class, or utility.",
  },
  smells: {
    summary:
      "Number of suspicious patterns flagged across the project.",
    formula: "count( RuleViolations )",
    detail:
      "Each flag points at a likely design, error-prone, performance, or best-practice issue. Treat the trend as the signal — a sudden jump usually means a recent change introduced a new category of issue worth looking at.",
  },
  coupling: {
    summary:
      "How entangled the most-connected classes are with the rest of the system.",
    formula: "p90 ( CouplingBetweenObjects per class )",
    detail:
      "Tightly coupled classes are hard to change in isolation — touching one tends to ripple through several others. The score reflects the worst offenders, so it drops when you sever a few dependencies on the most tangled classes.",
  },
  cohesion: {
    summary:
      "How focused each class is — do its methods belong together?",
    formula: "mean ( TightClassCohesion per class )",
    detail:
      "Low cohesion means a class is doing several unrelated jobs and is a candidate to split. A score near 1 means each class has a single, focused responsibility; near 0 suggests god-class behaviour.",
  },
}
