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
      "How hard the code is to follow at a glance — averaged over the methods you touched.",
    formula: "mean ( cognitive complexity per method ∈ touched files )",
    detail:
      "Campbell 2018's cognitive complexity, per method. Aggregated as the mean across methods whose file is in the trajectory-touched set — the union of every file changed across the session, held fixed throughout. Mean (rather than Σ) rewards Extract Method: splitting a heavy method into smaller helpers reduces the average even though Σ would mechanically increase. A high score means readers will struggle to hold a typical touched method in their head — usually because of deep nesting, chained conditionals, or jumps that break linear flow.",
  },
  readability: {
    summary:
      "How approachable the touched code looks to a new reader.",
    formula:
      "100 × 0.20 · ( LineLength + Indentation + IdentifierLength + (1 − SingleLetterRate) + DictionaryWordRate )",
    detail:
      "Uniform 0.20 blend of five Buse & Weimer 2010 features (line length, indentation depth, identifier length, single-letter rate, dictionary-word rate), evaluated on the touched-file subset with a line-count weighting so larger files contribute proportionally more. Equal weights follow Laplace's principle of insufficient reason — no in-domain calibration data distinguishes the relative importance of the five features. Shorter lines, shallower indentation, descriptive multi-letter names, and recognisable dictionary words all push the score up.",
  },
  duplication: {
    summary:
      "The share of the touched code that is copy-pasted from somewhere else.",
    formula: "( DuplicatedLines ∈ touched / TotalLines ∈ touched ) × 100",
    detail:
      "Touched-file duplication rate: CPD detects clones across the whole codebase, but the numerator counts only occurrences inside the trajectory-touched set, divided by the total line count of touched files. This makes local deduplication visible in the score — a 400-token Extract Method in a small touched footprint moves this rate substantially, while it would barely move the whole-codebase share. Duplicated logic means a bug fix has to be applied in several places; extract the shared piece into a helper, base class, or utility.",
  },
  smells: {
    summary:
      "The number of suspicious patterns flagged across the touched files.",
    formula: "count ( RuleViolations ∈ touched files )",
    detail:
      "PMD rule violations restricted to files in the trajectory-touched set — Marinescu 2004's detection-strategies approach to operationalising Fowler-style smells as thresholded structural rules. Unweighted by severity: priority weighting is reserved for the separate process-score smell ledger so we don't double-count severity across the cleanliness composite and the process penalty. A sudden jump usually means a recent change in your touched footprint introduced a new category of issue.",
  },
  coupling: {
    summary:
      "How dependent the classes you touched are on the rest of the system.",
    formula: "mean ( CouplingBetweenObjects per class ∈ touched files )",
    detail:
      "Chidamber & Kemerer 1994's CBO, averaged over classes whose file lies in the trajectory-touched set. Mean (rather than P90) is used because the touched footprint is small — typically 3–10 classes — where percentile statistics collapse to near-max; restricting to the touched set already provides the hot-spot focus that whole-codebase aggregation would need percentiles to recover. Tightly coupled touched classes are hard to change in isolation; the score drops when you sever dependencies on them.",
  },
  cohesion: {
    summary:
      "How focused the classes you touched are — do their methods belong together?",
    formula: "mean ( TightClassCohesion per class ∈ touched files )",
    detail:
      "Bieman & Kang 1995's TCC (the fraction of method pairs that share access to at least one instance variable), averaged over classes in the trajectory-touched set. TCC is preferred over the LCOM family because the latter has five competing definitions whose values disagree on the same input. A score near 1 means each touched class has a single, focused responsibility; near 0 suggests god-class behaviour and is a candidate to split.",
  },
  process: {
    summary:
      "How well the refactoring process has gone up to here — cumulative.",
    formula:
      "50 + 50·CleanlinessGain − 28·BrokenFrac − 21·IntermediateDegradation − 14·TestsSkipped − 11·ManualWhenIde − 7·CommitGapEvents",
    detail:
      "A single 0..100 score that summarises the trajectory so far rather than the code at this instant. Weighted gain in code quality from the start (cognitive, coupling, duplication, readability, smells, cohesion — weights from the literature) pushes the score up; broken checkpoints, refactorings without follow-up tests, manual edits where the IDE could have done the refactoring, long stretches of green refactor checkpoints without committing, and intermediate degradation (running-peak dips — \"you got the code clean once, you don't get to forget that you broke it\") all push it down. Anchored at 50 so a flat trajectory isn't misread as perfect.",
  },
  cleanliness: {
    summary:
      "Relative composite code-quality score across the six dimensions, 0..100.",
    formula:
      "100 · (1/6) · ( Cognitive + Coupling + Duplication + Readability + Smells + Cohesion )",
    detail:
      "Each of the six sub-signals is min-max normalised against this session's observed range, then combined as a uniform 1/6 weighted average and scaled to 0..100. Uniform weighting follows Laplace's principle of insufficient reason — with no in-domain calibration corpus we have no basis to weight one sub-signal above another; sensitivity analysis tests the robustness of the choice. Every sub-signal also aggregates over the same trajectory-touched file set (the union of files you changed across the session, held fixed throughout) so the composite measures the quality of your working footprint, not the codebase at large. 100 means \"the cleanest checkpoint in this session\", 0 means \"the dirtiest\" — not absolute code quality, so cross-session comparison of the raw number isn't meaningful.",
  },
}
