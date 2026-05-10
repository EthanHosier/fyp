import type { DashboardViewModel, MetricVM } from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"

/**
 * Y gridlines + tick labels, Y-axis caption ("COMPLEXITY · wmc"),
 * X baseline + checkpoint-number tick labels. Renders inside the
 * translated `<g>` set up by trajectory-chart.
 *
 * X axis is "detected refactoring checkpoint number" — see
 * `vm.xAnchors` for the anchor metadata. Sub-edits between two
 * anchors are interpolated by relative time but get no tick label.
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
  const xTicks = vm.xAnchors

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
          key={t.xPos}
          x1={xs(t.xPos)}
          x2={xs(t.xPos)}
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
          key={t.xPos}
          x={xs(t.xPos)}
          y={innerH + 14}
          textAnchor="middle"
          className="fill-fg-3 font-mono"
          fontSize={10}
        >
          {t.label}
        </text>
      ))}
    </g>
  )
}
