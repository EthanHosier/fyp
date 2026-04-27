export type MetricId =
  | "cohesion"
  | "coupling"
  | "smells"
  | "duplication"
  | "readability"
  | "cognitive"
  | "process"

export type StatusTone = "pass" | "fail" | "unknown"

export type MetricTone =
  | "brand"
  | "brand-2"
  | "brand-3"
  | "brand-4"
  | "brand-5"
  | "brand-6"
  | "brand-7"

export type MetricVM = {
  id: MetricId
  label: string
  unit: string
  better: "lower" | "higher"
  group: "code" | "process"
  /** Stable visual identity used everywhere the metric is rendered —
   * sparkline, overlay checkbox, trajectory line, etc. */
  tone: MetricTone
}

export type CheckpointVM = {
  index: number
  /** "c0", "c1", … */
  label: string
  /** "t+MM:SS" relative to session start */
  tLabel: string
  /** absolute timestamp (ms) */
  timestamp: number
  /** relative ms since session start — used as the chart X coordinate */
  tMs: number
  sha: string
  shortSha: string
  /** short human description — first event type or short sha */
  description: string
  values: Partial<Record<MetricId, number>>
  build: StatusTone
  tests: StatusTone
  /** aggregate of build+tests used for the filmstrip dot */
  status: StatusTone
  churn: number
  /** Unified-diff patch for the transition into this checkpoint (from the
   *  previous checkpoint, or empty for the seed state). */
  patch: string
  /** Bucketed PMD violations (added / carried / resolved) plus running totals. */
  smells: CodeSmellsVM
  /** Cumulative process score 0..100 at this checkpoint. See process-score.ts. */
  processScore: number
  /** Decomposition of the process score into signed contributions. */
  processBreakdown: ProcessScoreBreakdown
}

export type ProcessScoreContribution = {
  id: "cleanliness" | "broken" | "smells" | "skipTests" | "manualIde"
  label: string
  /** Signed; sums (with `baseline`) to `total` before clamping. */
  points: number
  /** Short human-readable explanation, e.g. "5 of 12 checkpoints broken (42%)". */
  detail: string
}

export type ProcessScoreBreakdown = {
  /** Final score in [0, 100], post-clamp. */
  total: number
  /** Anchor score before any contributions. */
  baseline: number
  contributions: ProcessScoreContribution[]
  /** True when the unclamped sum was outside [0, 100]. */
  clamped: boolean
}

export type ProcessScoreResult = {
  score: number
  breakdown: ProcessScoreBreakdown
}

/** Single PMD violation reshaped for UI consumption — folds in the
 * trajectory metadata (`firstSeenAtSha`) so consumers don't have to
 * cross-index against the raw report. */
export type CodeSmellVM = {
  rule: string
  ruleSet: string
  /** PMD severity 1..5; 1 = highest. */
  priority: number
  file: string
  beginLine: number
  endLine: number
  message: string
  /** Self-contained mini unified-diff text wrapping the snippet, ready
   *  to hand to `parsePatchFiles`. Null when the analysis pipeline
   *  couldn't read the source. */
  snippetPatch: string | null
  /** SHA of the earliest checkpoint at which this logical violation was
   *  first observed (chained forward through line-mapping). */
  firstSeenAtSha: string
}

/** Per-checkpoint violation buckets. `added` are first-seen at this
 *  checkpoint, `carried` are inherited from earlier, `resolved` are
 *  prev-checkpoint violations that no longer fire here (rendered with
 *  prev-side line numbers and prev-side snippet). */
export type CodeSmellsVM = {
  added: CodeSmellVM[]
  carried: CodeSmellVM[]
  resolved: CodeSmellVM[]
  /** `added.length + carried.length`. */
  totalNow: number
  /** Total violations at the previous checkpoint, or 0 for the seed. */
  totalPrev: number
  /** `totalNow - totalPrev`. */
  delta: number
}

export type IntervalVM = {
  /** index of the "from" checkpoint */
  index: number
  from: number
  to: number
  durationMs: number
  build: StatusTone
  tests: StatusTone
  status: StatusTone
  churn: number
}

export type SessionVM = {
  name: string
  projectName: string
  branch: string | null
  startedAt: number
  durationMs: number
  checkpointCount: number
  commitHash: string | null
}

export type TrajectoryVM = {
  totalChurn: number
  totalFilesTouched: number
  totalElapsedMs: number
  totalBrokenMs: number
}

export type RefactoringStepVM = {
  /** 0-based, chronological, matches server stepIndex */
  index: number
  /** Index into checkpoints[] — the checkpoint whose state this step's
   *  metric values come from (the window's toSha). */
  checkpointIndex: number
  timestamp: number
  /** relative ms since session start — X coordinate on the chart */
  tMs: number
  tLabel: string
  /** RefactoringMiner display name, e.g. "Extract Method" */
  refactoringType: string
  /** human-readable description from RefactoringMiner */
  description: string
  fromSha: string
  toSha: string
  shortFromSha: string
  shortToSha: string
  ideRelevant: boolean
  /** True iff a REFACTORING_FINISHED event landed on the toSha
   *  checkpoint — distinguishes IDE-performed vs manually-made refactors. */
  wasPerformedByIde: boolean
  /** True iff the toSha checkpoint has a TEST_RUN_FINISHED event — i.e.
   *  the user ran tests right after this refactoring. */
  userRanTests: boolean
  /** Hunk-filtered unified-diff patch scoped to the refactoring's
   *  left/right files — empty if the server didn't produce one. */
  patch: string
}

/**
 * One IDE-driven alternative path the analysis pipeline synthesised in
 * place of a manual multi-checkpoint refactoring. Renders as a dashed
 * branch leaving the user's actual trajectory at [fromCheckpointIndex],
 * passing through the synthesised alt checkpoint's metric value, and
 * rejoining at [toCheckpointIndex].
 */
export type AlternativeTrajectoryVM = {
  /** Server stepIndex — the underlying RefactoringStep this alternative
   *  was synthesised for. Doubles as the join key into the patches map. */
  index: number
  /** Index into checkpoints[] — the user's pre-state. */
  fromCheckpointIndex: number
  /** Index into checkpoints[] — the user's post-state we're comparing
   *  against. */
  toCheckpointIndex: number
  /** Short label rendered next to the branch's mid-point. Derived from
   *  the typed RefactoringSpec (e.g. "Extract Method"). */
  label: string
  /** Synthesised alt SHA — short form for display in detail panels. */
  altSha: string
  shortAltSha: string
  /** Branch ref under which the alt commit lives (e.g. "alt/2"). */
  branchRef: string
  /** Per-metric value at the alt checkpoint, computed identically to
   *  CheckpointVM.values so the chart can plot it directly. */
  altValues: Partial<Record<MetricId, number>>
  build: StatusTone
  tests: StatusTone
  status: StatusTone
  /** Total churn introduced by the alt commit (`fromSha → altSha`). */
  altChurn: number
  /** Unified-diff patch for `fromSha → altSha`. */
  patch: string
}

export type DashboardViewModel = {
  session: SessionVM
  metrics: MetricVM[]
  checkpoints: CheckpointVM[]
  intervals: IntervalVM[]
  refactoringSteps: RefactoringStepVM[]
  alternativeTrajectories: AlternativeTrajectoryVM[]
  trajectory: TrajectoryVM | undefined
}

export type Selection =
  | { kind: "checkpoint"; index: number }
  | { kind: "interval"; index: number }
  | { kind: "refactoring"; index: number }
  | { kind: "alternative"; index: number }
  // Click on the build / tests rail below the chart. `intervalIndex`
  // points at the first interval in a merged same-status run; the
  // detail panel walks forward to compute the run's total duration.
  | { kind: "status"; intervalIndex: number; statusKind: "build" | "tests" }
  | null

export type Layers = {
  buildIntervals: boolean
  testIntervals: boolean
  alternativeTrajectories: boolean
}
