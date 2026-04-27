import { ToneChip } from "@/components/tone-chip"
import type { CheckpointVM, DashboardViewModel, MetricVM, RefactoringStepVM } from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"

/**
 * Non-interactive overlay — one severity glyph per checkpoint that has a
 * process-signal pitfall. Sits above the dot (connected by a faint tick)
 * so the user can scan the trajectory for problem points without opening
 * the detail panel for each step.
 *
 * Tone is the worst signal that fires on the checkpoint. Mirrors what the
 * detail panel's "Process Signals" section surfaces:
 *  - `bad` (red X-in-circle): a refactoring landing here wasn't followed
 *    by a test run.
 *  - `warn` (amber rounded triangle with `!`): a refactoring here had an
 *    IDE-equivalent action but was done by hand, OR new code smells were
 *    introduced at this checkpoint.
 *
 * Multiple signals on the same checkpoint collapse to a single glyph at
 * the worst tone; the panel still lists each one individually.
 */
export function ChartRefactoringGlyphs({
  vm,
  primary,
  scales,
}: {
  vm: DashboardViewModel
  primary: MetricVM
  scales: ChartScales
}) {
  const { xs, ys } = scales

  // Index refactoring steps by checkpoint so the glyph can reflect every
  // step landing on the same SHA (rare in practice but possible).
  const stepsByCheckpoint = new Map<number, RefactoringStepVM[]>()
  for (const s of vm.refactoringSteps) {
    const arr = stepsByCheckpoint.get(s.checkpointIndex) ?? []
    arr.push(s)
    stepsByCheckpoint.set(s.checkpointIndex, arr)
  }

  return (
    <g className="pointer-events-none">
      {vm.checkpoints.map((c) => {
        const v = c.values[primary.id]
        if (typeof v !== "number") return null

        const steps = stepsByCheckpoint.get(c.index) ?? []
        const tone = signalTone(c, steps)
        if (!tone) return null

        const cx = xs(c.tMs)
        const cy = ys(v)
        const gy = cy - 17

        return (
          <g key={c.index}>
            <line
              x1={cx}
              x2={cx}
              y1={gy + 5}
              y2={cy - 8}
              className="stroke-border-strong"
              strokeOpacity={0.55}
              strokeWidth={0.8}
            />
            <ProcessSignalGlyph
              x={cx}
              y={gy}
              tone={tone}
              tooltip={signalTooltip(c, steps)}
            />
          </g>
        )
      })}
    </g>
  )
}

type Tone = "bad" | "warn"

function signalTone(c: CheckpointVM, steps: RefactoringStepVM[]): Tone | null {
  if (steps.some((s) => !s.userRanTests)) return "bad"
  const ideAble = steps.some((s) => s.ideRelevant && !s.wasPerformedByIde)
  const newSmells = c.smells.added.length > 0
  if (ideAble || newSmells) return "warn"
  return null
}

function signalTooltip(c: CheckpointVM, steps: RefactoringStepVM[]): string {
  const parts: string[] = []
  if (steps.some((s) => !s.userRanTests)) {
    parts.push("No tests were run after a refactoring at this checkpoint.")
  }
  for (const s of steps) {
    if (s.ideRelevant && !s.wasPerformedByIde) {
      parts.push(`Manual refactor — IDE could have done this: ${s.refactoringType}.`)
    }
  }
  const added = c.smells.added.length
  if (added > 0) {
    parts.push(`${added} new code smell${added === 1 ? "" : "s"} introduced.`)
  }
  return parts.join(" ")
}

function ProcessSignalGlyph({
  x,
  y,
  tone,
  tooltip,
}: {
  x: number
  y: number
  tone: Tone
  tooltip: string
}) {
  const SIZE = 14
  return (
    <g transform={`translate(${x - SIZE / 2},${y - SIZE / 2})`}>
      <title>{tooltip}</title>
      <ToneChip tone={tone} size={SIZE} />
    </g>
  )
}
