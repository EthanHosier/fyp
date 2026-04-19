import type { DashboardViewModel, MetricVM } from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"

/**
 * Y gridlines + tick labels, Y-axis caption ("COMPLEXITY · wmc"),
 * X baseline + cX labels. Renders inside the translated `<g>` set up by
 * trajectory-chart.
 */
function tickDecimals(ticks: number[]): number {
  if (ticks.length < 2) return 0
  const step = Math.abs(ticks[1] - ticks[0])
  if (step >= 10) return 0
  if (step >= 1) return 1
  if (step >= 0.1) return 2
  return 3
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
  const every = vm.checkpoints.length > 10 ? 2 : 1

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

      {vm.checkpoints.map((c) => (
        <line
          key={c.index}
          x1={xs(c.index)}
          x2={xs(c.index)}
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

      {vm.checkpoints.map((c) => {
        const show =
          c.index % every === 0 || c.index === vm.checkpoints.length - 1
        if (!show) return null
        return (
          <text
            key={c.index}
            x={xs(c.index)}
            y={innerH + 14}
            textAnchor="middle"
            className="fill-fg-3 font-mono"
            fontSize={10}
          >
            {c.label}
          </text>
        )
      })}
    </g>
  )
}
