import type { DashboardViewModel, MetricVM } from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { formatTLabel } from "@/lib/format"

/**
 * Y gridlines + tick labels, Y-axis caption ("COMPLEXITY · wmc"),
 * X baseline + time tick labels (t+MM:SS). Renders inside the translated
 * `<g>` set up by trajectory-chart.
 */
function tickDecimals(ticks: number[]): number {
  if (ticks.length < 2) return 0
  const step = Math.abs(ticks[1] - ticks[0])
  if (step >= 10) return 0
  if (step >= 1) return 1
  if (step >= 0.1) return 2
  return 3
}

const MS = 1
const S = 1000 * MS
const M = 60 * S
const H = 60 * M
/** "Nice" time step ladder — chosen so tick labels land on round
 *  seconds / minutes / hours regardless of total duration. */
const NICE_TIME_STEPS = [
  5 * S, 10 * S, 15 * S, 30 * S,
  1 * M, 2 * M, 5 * M, 10 * M, 15 * M, 30 * M,
  1 * H, 2 * H, 6 * H, 12 * H, 24 * H,
]

function timeTicks(durationMs: number, approxCount: number): number[] {
  if (durationMs <= 0) return [0]
  const target = durationMs / approxCount
  const step =
    NICE_TIME_STEPS.find((s) => s >= target) ??
    NICE_TIME_STEPS[NICE_TIME_STEPS.length - 1]
  const out: number[] = []
  for (let t = 0; t <= durationMs + 1; t += step) out.push(t)
  return out
}

export function ChartAxes({
  vm,
  primary,
  scales,
}: {
  vm: DashboardViewModel
  primary: MetricVM
  scales: ChartScales
}) {
  const { xs, ys, innerW, innerH, yTicks, margin } = scales
  const decimals = tickDecimals(yTicks)
  const xTicks = timeTicks(xs.invert(innerW), 7)

  return (
    <g>
      {yTicks.map((t) => (
        <g key={t} transform={`translate(0,${ys(t).toFixed(1)})`}>
          <line
            x1={0}
            x2={innerW}
            className="stroke-border"
            strokeDasharray="2 4"
          />
          <text
            x={-8}
            y={3}
            textAnchor="end"
            className="fill-fg-4 font-mono"
            fontSize={10.5}
          >
            {t.toFixed(decimals)}
          </text>
        </g>
      ))}

      <text
        x={-margin.left + 6}
        y={-20}
        className="fill-fg-3 font-mono"
        fontSize={10.5}
      >
        {primary.label.toUpperCase()} · {primary.unit}
      </text>

      {xTicks.map((t) => (
        <line
          key={t}
          x1={xs(t)}
          x2={xs(t)}
          y1={0}
          y2={innerH}
          className="stroke-border"
          strokeOpacity={0.25}
        />
      ))}

      <line
        x1={0}
        x2={innerW}
        y1={innerH}
        y2={innerH}
        className="stroke-border-strong"
      />

      {xTicks.map((t) => (
        <text
          key={t}
          x={xs(t)}
          y={innerH + 14}
          textAnchor="middle"
          className="fill-fg-3 font-mono"
          fontSize={10}
        >
          {formatTLabel(t)}
        </text>
      ))}
    </g>
  )
}
