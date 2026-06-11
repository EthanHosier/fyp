import { ToneChip } from "@/components/tone-chip"
import type { CheckpointVM, DashboardViewModel, MetricVM, RefactoringStepVM } from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"

/** Non-interactive overlay: one severity glyph per checkpoint that has a process-signal pitfall. */
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

  // One glyph per refactoring step (at the step's own xPos). When
  // multiple steps share a landing checkpoint they each take their own
  // slot on the X axis, so we render one glyph per step rather than
  // collapsing them into a single per-checkpoint marker.
  return (
    <g className="pointer-events-none">
      {vm.refactoringSteps.map((s) => {
        const c = vm.checkpoints[s.checkpointIndex]
        if (!c) return null
        const v = c.values[primary.id]
        if (typeof v !== "number") return null

        const tone = signalTone(c, [s])
        if (!tone) return null

        const cx = xs(s.xPos)
        const cy = ys(v)
        const gy = cy - 17

        return (
          <g key={s.index}>
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
              tooltip={signalTooltip(c, [s])}
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
