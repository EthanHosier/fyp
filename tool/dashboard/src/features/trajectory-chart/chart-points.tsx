import type { DashboardViewModel, MetricVM, Selection } from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { TONE_TEXT } from "@/lib/metric-tone"
import { cn } from "@/lib/utils"

/**
 * Sub-point layer — every checkpoint as a small muted dot on the primary
 * line. Refactoring steps are rendered on top via `ChartRefactoringPoints`
 * as the primary dots; these remain clickable for checkpoints that aren't
 * a refactoring-step target.
 *
 * When `showAll` is false only the session anchors (first + last
 * checkpoint) render, so the user can still see where the trajectory
 * starts and ends even with substeps toggled off.
 */
export function ChartPoints({
  vm,
  primary,
  scales,
  selection,
  showAll,
  onSelect,
}: {
  vm: DashboardViewModel
  primary: MetricVM
  scales: ChartScales
  selection: Selection
  showAll: boolean
  onSelect: (s: Selection) => void
}) {
  const { xs, ys } = scales
  const lastIdx = vm.checkpoints.length - 1

  return (
    <g className={cn(TONE_TEXT[primary.tone])}>
      {vm.checkpoints.map((c) => {
        const isAnchor = c.index === 0 || c.index === lastIdx
        if (!showAll && !isAnchor) return null
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
