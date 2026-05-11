export type MetricId =
  | "cohesion"
  | "coupling"
  | "smells"
  | "duplication"
  | "readability"
  | "cognitive"
  | "process"
  | "cleanliness"

export type StatusTone = "pass" | "fail" | "unknown"

export type MetricTone =
  | "brand"
  | "brand-2"
  | "brand-3"
  | "brand-4"
  | "brand-5"
  | "brand-6"
  | "brand-7"
  | "brand-8"
  | "brand-9"

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
  /** relative ms since session start (kept for tooltips / time labels) */
  tMs: number
  /** Chart X coordinate. Detected refactoring checkpoints (and the
   *  start + end terminator) sit at integer positions 0..K; sub-edits
   *  in between are interpolated by their `tMs` within the surrounding
   *  anchor pair. See `vm.xAnchors` for the anchor metadata. */
  xPos: number
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
  /** Clone groups added or resolved at this checkpoint vs. the previous one.
   *  Only churn — carried groups intentionally omitted. */
  duplications: DuplicationsVM
  /** Cumulative process score 0..100 at this checkpoint. See process-score.ts. */
  processScore: number
  /** Decomposition of the process score into signed contributions. */
  processBreakdown: ProcessScoreBreakdown
  /** Cleanliness composite 0..100 at this checkpoint, or `null` when no
   *  underlying metrics are normalisable (degenerate trajectory). */
  cleanlinessScore: number | null
  /** Per-sub-metric decomposition of the cleanliness composite. */
  cleanlinessBreakdown: CleanlinessBreakdown | null
}

export type ProcessScoreContribution = {
  id:
    | "cleanliness"
    | "degradation"
    | "broken"
    | "smells"
    | "skipTests"
    | "manualIde"
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

/** One sub-metric's contribution to the cleanliness composite. */
export type CleanlinessContribution = {
  id: MetricId
  label: string
  /** Literature-informed weight, 0..1. */
  weight: number
  /** Normalised value 0..1 where 1 = best (direction already flipped). */
  normalised: number
  /** Raw value at this checkpoint, before normalisation. */
  raw: number
  /** Points contributed to the displayed 0..100 score: `weight·normalised·100`. */
  points: number
}

export type CleanlinessBreakdown = {
  /** 0..100, rounded. */
  total: number
  contributions: CleanlinessContribution[]
  /** True iff at least one of the literature-weighted metrics was excluded
   *  (missing or degenerate range) and the remaining weights had to be
   *  re-based — the breakdown card surfaces this so a 100 reading isn't
   *  mistaken for a clean sweep across all six dimensions. */
  rebased: boolean
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

/** One occurrence of a clone group, reshaped for UI consumption. */
export type DuplicationOccurrenceVM = {
  file: string
  beginLine: number
  endLine: number
  /** Self-contained mini unified-diff text wrapping the snippet, ready to
   *  hand to `parsePatchFiles`. Null when the analysis pipeline couldn't
   *  read the source. */
  snippetPatch: string | null
}

/** A single clone group flagged at this checkpoint, tagged with whether
 *  it appeared (`new`) or disappeared (`resolved`) since the previous
 *  checkpoint. Carried groups are intentionally not surfaced. */
export type DuplicationGroupVM = {
  /** Snippet body hash; stable across pure code-motion. */
  identity: string
  tokens: number
  lines: number
  occurrences: DuplicationOccurrenceVM[]
  state: "new" | "resolved"
  /** SHA where this group was first observed (== current sha for `new`). */
  firstSeenAtSha: string
}

export type DuplicationsVM = {
  added: DuplicationGroupVM[]
  resolved: DuplicationGroupVM[]
  /** Total clone groups at this checkpoint. */
  totalNow: number
  /** Total clone groups at the previous checkpoint, or 0 for the seed. */
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
  /** relative ms since session start (kept for time labels) */
  tMs: number
  /** Chart X coordinate (matches `vm.checkpoints[checkpointIndex].xPos`). */
  xPos: number
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
  /** Per-applied-step view of the alt chain. Single-step alts have one
   *  entry; multi-step reorder orderings have N. The terminal step
   *  matches the scalar fields above (label/altSha/branchRef/...). */
  steps: AlternativeStepVM[]
}

export type AlternativeStepVM = {
  altSha: string
  shortAltSha: string
  stepIndex: number
  label: string
  branchRef: string
  altValues: Partial<Record<MetricId, number>>
  build: StatusTone
  tests: StatusTone
  altChurn: number
  patch: string
  /** CheckpointVM-shaped snapshot for this alt step — lets the detail
   *  panel render the same metrics layout used for normal checkpoints
   *  when the user clicks an alt dot. `index` is the sentinel `-1` so
   *  joins-by-array-index silently skip it. */
  cpVm: CheckpointVM
}

export type DashboardViewModel = {
  session: SessionVM
  metrics: MetricVM[]
  checkpoints: CheckpointVM[]
  intervals: IntervalVM[]
  refactoringSteps: RefactoringStepVM[]
  alternativeTrajectories: AlternativeTrajectoryVM[]
  /** Commits the user made on their working repo during the session. */
  commitMarkers: CommitMarkerVM[]
  trajectory: TrajectoryVM | undefined
  /** Tick anchors for the chart's X axis. The chart's X coordinate is
   *  "checkpoint number" — start, every detected refactoring checkpoint,
   *  and the session-end terminator each sit at integer positions
   *  0..K. Sub-edits between two anchors are interpolated by their
   *  relative time. */
  xAnchors: XAnchorVM[]
}

/** A commit landed on the user's working repo during the session. Plotted
 *  as a vertical tick on the trajectory chart's x-axis at the time-
 *  interpolated `xPos`; hovering reveals the short SHA and first line of
 *  the commit message. Pure overlay — no selection / detail panel. */
export type CommitMarkerVM = {
  sha: string
  shortSha: string
  message: string
  /** Author/committer time (ms since epoch) from the reflog line. */
  timestamp: number
  /** Chart X coordinate, interpolated from checkpoint timestamps. */
  xPos: number
}

export type XAnchorVM = {
  /** Integer 0..K. */
  xPos: number
  /** Index into `checkpoints[]`. */
  checkpointIndex: number
  /** Tick label rendered on the X axis. "Start" / "End" / "R<n>". */
  label: string
}

export type Selection =
  | { kind: "checkpoint"; index: number }
  | { kind: "interval"; index: number }
  | { kind: "refactoring"; index: number }
  | { kind: "alternative"; index: number }
  // Click on one alt path's per-step dot (the synthesised checkpoint
  // for the k-th applied step). `altIndex` joins
  // `vm.alternativeTrajectories[].index`; `stepIndex` is the index
  // into that alt's `steps` array (NOT the user's stepIndex).
  | { kind: "altCheckpoint"; altIndex: number; stepIndex: number }
  // Click on a segment of an alt path. Segments index 0..N where 0 =
  // user fromCp → alt[0], k = alt[k-1] → alt[k] for 1 <= k <= N-1, and
  // N = alt[N-1] → user toCp.
  | { kind: "altInterval"; altIndex: number; segmentIndex: number }
  // Click on the build / tests rail below the chart. `intervalIndex`
  // points at the first interval in a merged same-status run; the
  // detail panel walks forward to compute the run's total duration.
  | { kind: "status"; intervalIndex: number; statusKind: "build" | "tests" }
  | null

export type Layers = {
  buildIntervals: boolean
  testIntervals: boolean
  alternativeTrajectories: boolean
  userCommits: boolean
}
