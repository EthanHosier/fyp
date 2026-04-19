import type { DashboardViewModel, Selection, StatusTone } from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { cn } from "@/lib/utils"

export const INTERVAL_RAIL_HEIGHT = 18
export const INTERVAL_RAIL_OFFSET = 22

/**
 * Thin status rail below the X-axis. One rect per interval, coloured
 * by the combined build/tests status. Clicks dispatch an interval
 * selection to the dashboard store.
 */
export function ChartIntervalRail({
  vm,
  scales,
  onSelect,
}: {
  vm: DashboardViewModel
  scales: ChartScales
  onSelect: (s: Selection) => void
}) {
  const { xs, innerH } = scales
  const y = innerH + INTERVAL_RAIL_OFFSET

  return (
    <g transform={`translate(0,${y})`}>
      <text
        x={-8}
        y={INTERVAL_RAIL_HEIGHT / 2 + 3}
        textAnchor="end"
        className="fill-fg-4 font-mono"
        fontSize={9.5}
      >
        BUILD
      </text>
      {vm.intervals.map((iv) => {
        const x0 = xs(iv.from)
        const x1 = xs(iv.to)
        return (
          <rect
            key={iv.index}
            x={x0}
            y={0}
            width={Math.max(0, x1 - x0)}
            height={INTERVAL_RAIL_HEIGHT}
            className={cn(
              STATUS_FILL[iv.status],
              STATUS_STROKE[iv.status],
              "cursor-pointer",
            )}
            strokeOpacity={0.4}
            strokeWidth={0.75}
            onClick={() => onSelect({ kind: "interval", index: iv.index })}
          />
        )
      })}
    </g>
  )
}

const STATUS_FILL: Record<StatusTone, string> = {
  pass: "fill-good/20",
  fail: "fill-bad/25",
  unknown: "fill-unknown/20",
}

const STATUS_STROKE: Record<StatusTone, string> = {
  pass: "stroke-good",
  fail: "stroke-bad",
  unknown: "stroke-unknown",
}
