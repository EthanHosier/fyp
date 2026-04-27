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
      "The share of the codebase that is copy-pasted from somewhere else.",
    formula: "( DuplicatedLines / TotalLines ) × 100",
    detail:
      "Duplicated logic means a bug fix has to be applied in several places and it's easy to miss one. A rising trend is a hint to extract the shared piece into a helper, base class, or utility.",
  },
  smells: {
    summary:
      "The number of suspicious patterns flagged across the project.",
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
  process: {
    summary:
      "How well the refactoring process has gone up to here — cumulative.",
    formula:
      "50 + 35·CleanlinessGain − 20·BrokenFrac − 15·NetSmells − 10·TestsSkipped − 8·ManualWhenIde",
    detail:
      "A single 0..100 score that summarises the trajectory so far rather than the code at this instant. Weighted gain in code quality from the start (cognitive, coupling, duplication, readability, smells, cohesion — weights from the literature) pushes the score up; broken checkpoints, net new smells, refactorings without follow-up tests, and manual edits where the IDE could have done the refactoring all push it down. Anchored at 50 so a flat trajectory isn't misread as perfect.",
  },
  cleanliness: {
    summary:
      "Relative composite code-quality score across the six dimensions, 0..100.",
    formula:
      "100 · ( 0.25·Cognitive + 0.20·Coupling + 0.20·Duplication + 0.15·Readability + 0.15·Smells + 0.05·Cohesion )",
    detail:
      "Each sub-metric is min-max normalised against this trajectory's observed range, so 100 means \"the cleanest checkpoint in this session\" and 0 means \"the dirtiest\" — not absolute code quality. Weights come from the literature: cognitive complexity (Campbell 2018) and coupling (Chidamber & Kemerer 1994) are weighted heaviest as the strongest comprehension/defect predictors; cohesion is light because LCOM-family metrics are noisy and contested. Because the score is session-relative, comparing 60 in one session against 60 in another isn't meaningful — read it as direction within a session, not absolute quality.",
  },
}
