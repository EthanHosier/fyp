import type { DashboardViewModel, MetricVM, Selection } from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { TONE_TEXT } from "@/lib/metric-tone"
import { cn } from "@/lib/utils"

/**
 * Primary dots — one per detected refactoring step, positioned at the
 * step's `toCheckpointIndex` on the primary metric line. Sits on top of
 * `ChartPoints` (the smaller checkpoint sub-dots) so clicks here land on
 * the refactoring, not the underlying checkpoint.
 *
 * Two refactorings that share a window stack at the same (x, y); the last
 * drawn handles the click. Real sessions rarely surface this because edit
 * bursts are fine-grained enough to isolate single refactorings.
 */
export function ChartRefactoringPoints({
  vm,
  primary,
  scales,
  selection,
  onSelect,
}: {
  vm: DashboardViewModel
  primary: MetricVM
  scales: ChartScales
  selection: Selection
  onSelect: (s: Selection) => void
}) {
  const { xs, ys } = scales

  return (
    <g className={cn(TONE_TEXT[primary.tone])}>
      {vm.refactoringSteps.map((s) => {
        const c = vm.checkpoints[s.checkpointIndex]
        if (!c) return null
        const v = c.values[primary.id]
        if (typeof v !== "number") return null
        const selected =
          selection?.kind === "refactoring" && selection.index === s.index
        const r = selected ? 6 : 4.5
        return (
          <g
            key={s.index}
            className="cursor-pointer"
            onClick={() => onSelect({ kind: "refactoring", index: s.index })}
          >
            <circle cx={xs(s.checkpointIndex)} cy={ys(v)} r={r + 6} fill="transparent" />
            <circle
              cx={xs(s.checkpointIndex)}
              cy={ys(v)}
              r={r}
              fill="currentColor"
              stroke="var(--color-bg-1)"
              strokeWidth={2}
            />
            {selected ? (
              <circle
                cx={xs(s.checkpointIndex)}
                cy={ys(v)}
                r={r + 4}
                fill="none"
                stroke="currentColor"
                strokeOpacity={0.45}
                strokeWidth={1.2}
              />
            ) : null}
          </g>
        )
      })}
    </g>
  )
}
