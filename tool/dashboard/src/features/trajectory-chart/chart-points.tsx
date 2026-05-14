import type { DashboardViewModel, MetricVM, Selection } from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { TONE_TEXT } from "@/lib/metric-tone"
import { useDashboardStore } from "@/stores/dashboard-store"
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
  const highlightedCheckpointIndex = useDashboardStore(
    (s) => s.highlightedCheckpointIndex,
  )

  // Flash policy: a manual highlight (e.g. the rework alt's "Code added
  // at <ckpt>" link setting `highlightedCheckpointIndex`) always wins,
  // and flashes that single checkpoint. With no manual highlight, the
  // chart falls back to flashing every divergence-point's anchor step
  // by default — so DPs draw the eye on first load. Clicking any link
  // that sets a manual highlight collapses the set to just that one.
  const flashingIndexes: ReadonlySet<number> =
    highlightedCheckpointIndex != null
      ? new Set([highlightedCheckpointIndex])
      : new Set(vm.divergencePoints.map((dp) => dp.stepIndex))

  return (
    <g className={cn(TONE_TEXT[primary.tone])}>
      {vm.checkpoints.map((c) => {
        const v = c.values[primary.id]
        if (typeof v !== "number") return null
        const selected =
          selection?.kind === "checkpoint" && selection.index === c.index
        const highlighted = flashingIndexes.has(c.index)
        const r = selected ? 3 : 2
        return (
          <g
            key={c.index}
            className="cursor-pointer"
            onClick={() => onSelect({ kind: "checkpoint", index: c.index })}
          >
            <circle cx={xs(c.xPos)} cy={ys(v)} r={r + 6} fill="transparent" />
            <circle
              cx={xs(c.xPos)}
              cy={ys(v)}
              r={r}
              fill="currentColor"
              fillOpacity={selected ? 0.9 : 0.5}
            />
            {selected ? (
              <circle
                cx={xs(c.xPos)}
                cy={ys(v)}
                r={r + 3}
                fill="none"
                stroke="currentColor"
                strokeOpacity={0.35}
                strokeWidth={1}
              />
            ) : null}
            {highlighted ? (
              <circle
                cx={xs(c.xPos)}
                cy={ys(v)}
                r={r + 6}
                className="fill-none stroke-brand-2"
                strokeWidth={2}
                strokeOpacity={0.9}
              >
                <animate
                  attributeName="r"
                  values={`${r + 4};${r + 9};${r + 4}`}
                  dur="1.6s"
                  repeatCount="indefinite"
                />
                <animate
                  attributeName="stroke-opacity"
                  values="0.9;0.3;0.9"
                  dur="1.6s"
                  repeatCount="indefinite"
                />
              </circle>
            ) : null}
          </g>
        )
      })}
    </g>
  )
}
