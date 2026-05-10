import type {
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
  const primaryPoints = chartTimelinePoints(vm, primary).map((p) => ({
    x: xs(p.t),
    y: ys(p.v),
  }))

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
  const points = chartTimelinePoints(vm, metric)
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

/**
 * Build the line's vertex sequence by walking xAnchors (one entry per
 * slot — Start, every refactoring step, End) and interleaving any
 * non-anchor sub-edit checkpoints by their interpolated `xPos`. When
 * multiple steps share a landing checkpoint, the line emits one vertex
 * per slot at the same y, so the polyline stays flat across the
 * shared-cp cluster instead of cutting back through the next cp's
 * value. */
function chartTimelinePoints(
  vm: DashboardViewModel,
  metric: MetricVM,
): { t: number; v: number }[] {
  const slotCpIdxs = new Set(vm.xAnchors.map((a) => a.checkpointIndex))
  const fromAnchors = vm.xAnchors.map((a) => ({
    t: a.xPos,
    v: vm.checkpoints[a.checkpointIndex]?.values[metric.id],
  }))
  const fromNonAnchors = vm.checkpoints
    .filter((c) => !slotCpIdxs.has(c.index))
    .map((c) => ({ t: c.xPos, v: c.values[metric.id] }))
  return [...fromAnchors, ...fromNonAnchors]
    .filter((p): p is { t: number; v: number } => typeof p.v === "number")
    .sort((a, b) => a.t - b.t)
}
