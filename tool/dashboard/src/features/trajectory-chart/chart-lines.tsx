import type {
  CheckpointVM,
  DashboardViewModel,
  MetricVM,
} from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { TONE_TEXT } from "@/lib/metric-tone"
import { linePath, linearScale } from "@/lib/scales"
import { cn } from "@/lib/utils"

/**
 * The line layer:
 *   1. Primary polyline — always painted in the metric's own tone.
 *      Build/test status is communicated by the rails below the chart,
 *      so the line itself stays focused on the metric trajectory.
 *   2. Each secondary metric as a dashed line in its own tone, using
 *      an independent Y scale so small-range metrics still read
 *      meaningfully against the primary.
 */
export function ChartLines({
  vm,
  primary,
  secondaries,
  scales,
}: {
  vm: DashboardViewModel
  primary: MetricVM
  secondaries: MetricVM[]
  scales: ChartScales
}) {
  const { xs, ys, innerH } = scales
  const primaryPoints = checkpointPoints(vm.checkpoints, primary, xs, ys)

  return (
    <>
      {primaryPoints.length >= 2 ? (
        <g className={cn(TONE_TEXT[primary.tone])}>
          <path
            d={linePath(primaryPoints)}
            fill="none"
            stroke="currentColor"
            strokeWidth={1.6}
            strokeOpacity={0.9}
          />
        </g>
      ) : null}

      {secondaries.map((m) => (
        <SecondaryLine key={m.id} vm={vm} metric={m} scales={scales} innerH={innerH} />
      ))}
    </>
  )
}

function SecondaryLine({
  vm,
  metric,
  scales,
  innerH,
}: {
  vm: DashboardViewModel
  metric: MetricVM
  scales: ChartScales
  innerH: number
}) {
  const points = vm.checkpoints
    .map((c) => ({ t: c.tMs, v: c.values[metric.id] }))
    .filter((p): p is { t: number; v: number } => typeof p.v === "number")
  if (points.length < 2) return null

  const values = points.map((p) => p.v)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const pad = (max - min) * 0.22 || 1
  const localY = linearScale([min - pad, max + pad], [innerH, 0])
  const xy = points.map((p) => ({ x: scales.xs(p.t), y: localY(p.v) }))

  return (
    <g className={cn(TONE_TEXT[metric.tone])}>
      <path
        d={linePath(xy)}
        fill="none"
        stroke="currentColor"
        strokeWidth={1.3}
        strokeOpacity={0.55}
        strokeDasharray="3 3"
      />
      {xy.map((p, i) => (
        <circle
          key={i}
          cx={p.x}
          cy={p.y}
          r={1.6}
          fill="currentColor"
          fillOpacity={0.7}
        />
      ))}
    </g>
  )
}

function checkpointPoints(
  checkpoints: CheckpointVM[],
  metric: MetricVM,
  xs: ChartScales["xs"],
  ys: ChartScales["ys"],
) {
  return checkpoints
    .map((c) => ({ t: c.tMs, v: c.values[metric.id] }))
    .filter((p): p is { t: number; v: number } => typeof p.v === "number")
    .map((p) => ({ x: xs(p.t), y: ys(p.v) }))
}
