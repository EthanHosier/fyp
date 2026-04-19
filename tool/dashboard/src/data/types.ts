export type MetricId =
  | "complexity"
  | "coupling"
  | "duplication"
  | "readability"
  | "churn"

export type StatusTone = "pass" | "fail" | "unknown"

export type MetricTone = "brand" | "brand-2" | "brand-3" | "brand-4" | "brand-5"

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

export type DashboardViewModel = {
  session: SessionVM
  metrics: MetricVM[]
  checkpoints: CheckpointVM[]
  intervals: IntervalVM[]
  trajectory: TrajectoryVM | undefined
}

export type Selection =
  | { kind: "checkpoint"; index: number }
  | { kind: "interval"; index: number }
  | null

export type Layers = {
  intervals: boolean
}
