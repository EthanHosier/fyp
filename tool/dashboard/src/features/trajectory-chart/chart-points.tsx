import type { DashboardViewModel, MetricVM, Selection } from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { TONE_TEXT } from "@/lib/metric-tone"
import { cn } from "@/lib/utils"

/**
 * Sub-point layer — every checkpoint as a small muted dot on the primary
 * line. Refactoring steps are rendered on top via `ChartRefactoringPoints`
 * as the primary dots; these remain clickable for checkpoints that aren't
 * a refactoring-step target.
 */
export function ChartPoints({
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
      {vm.checkpoints.map((c) => {
        const v = c.values[primary.id]
        if (typeof v !== "number") return null
        const selected =
          selection?.kind === "checkpoint" && selection.index === c.index
        const r = selected ? 3 : 2
        return (
          <g
            key={c.index}
            className="cursor-pointer"
            onClick={() => onSelect({ kind: "checkpoint", index: c.index })}
          >
            <circle cx={xs(c.index)} cy={ys(v)} r={r + 6} fill="transparent" />
            <circle
              cx={xs(c.index)}
              cy={ys(v)}
              r={r}
              fill="currentColor"
              fillOpacity={selected ? 0.9 : 0.5}
            />
            {selected ? (
              <circle
                cx={xs(c.index)}
                cy={ys(v)}
                r={r + 3}
                fill="none"
                stroke="currentColor"
                strokeOpacity={0.35}
                strokeWidth={1}
              />
            ) : null}
          </g>
        )
      })}
    </g>
  )
}
