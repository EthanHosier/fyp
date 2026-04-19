import type {
  CheckpointVM,
  DashboardViewModel,
  IntervalVM,
  MetricVM,
  StatusTone,
} from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { TONE_TEXT } from "@/lib/metric-tone"
import { linePath, linearScale } from "@/lib/scales"
import { cn } from "@/lib/utils"

/**
 * The line layer:
 *   1. Primary polyline — solid when intervals are off; when on, the
 *      line goes transparent and we draw one coloured segment per
 *      interval (good/bad/unknown) between consecutive checkpoints.
 *   2. Each secondary metric as a dashed line in its own tone, using
 *      an independent Y scale so small-range metrics still read
 *      meaningfully against the primary.
 */
export function ChartLines({
  vm,
  primary,
  secondaries,
  showIntervals,
  scales,
}: {
  vm: DashboardViewModel
  primary: MetricVM
  secondaries: MetricVM[]
  showIntervals: boolean
  scales: ChartScales
}) {
  const { xs, ys, innerH } = scales
  const primaryPoints = checkpointPoints(vm.checkpoints, primary, xs, ys)

  return (
    <>
      {showIntervals ? (
        <g>
          {vm.intervals.map((iv) => (
            <IntervalSegment key={iv.index} iv={iv} vm={vm} primary={primary} scales={scales} />
          ))}
        </g>
      ) : null}

      {primaryPoints.length >= 2 ? (
        <g className={cn(TONE_TEXT[primary.tone])}>
          <path
            d={linePath(primaryPoints)}
            fill="none"
            stroke="currentColor"
            strokeWidth={1.6}
            strokeOpacity={showIntervals ? 0 : 0.9}
          />
        </g>
      ) : null}

      {secondaries.map((m) => (
        <SecondaryLine key={m.id} vm={vm} metric={m} scales={scales} innerH={innerH} />
      ))}
    </>
  )
}

function IntervalSegment({
  iv,
  vm,
  primary,
  scales,
}: {
  iv: IntervalVM
  vm: DashboardViewModel
  primary: MetricVM
  scales: ChartScales
}) {
  const from = vm.checkpoints[iv.from]
  const to = vm.checkpoints[iv.to]
  const v0 = from.values[primary.id]
  const v1 = to.values[primary.id]
  if (typeof v0 !== "number" || typeof v1 !== "number") return null
  return (
    <line
      x1={scales.xs(from.index)}
      y1={scales.ys(v0)}
      x2={scales.xs(to.index)}
      y2={scales.ys(v1)}
      className={cn(STATUS_STROKE[iv.status], "cursor-pointer")}
      stroke="currentColor"
      strokeOpacity={iv.status === "pass" ? 0.55 : 0.9}
      strokeWidth={3}
      strokeLinecap="round"
    />
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
    .map((c) => ({ i: c.index, v: c.values[metric.id] }))
    .filter((p): p is { i: number; v: number } => typeof p.v === "number")
  if (points.length < 2) return null

  const values = points.map((p) => p.v)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const pad = (max - min) * 0.22 || 1
  const localY = linearScale([min - pad, max + pad], [innerH, 0])
  const xy = points.map((p) => ({ x: scales.xs(p.i), y: localY(p.v) }))

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
    .map((c) => ({ i: c.index, v: c.values[metric.id] }))
    .filter((p): p is { i: number; v: number } => typeof p.v === "number")
    .map((p) => ({ x: xs(p.i), y: ys(p.v) }))
}

const STATUS_STROKE: Record<StatusTone, string> = {
  pass: "text-good",
  fail: "text-bad",
  unknown: "text-unknown",
}
