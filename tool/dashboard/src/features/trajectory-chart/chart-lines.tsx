import type { DashboardViewModel, MetricVM } from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { TONE_TEXT } from "@/lib/metric-tone"
import { linePath } from "@/lib/scales"
import { cn } from "@/lib/utils"

/**
 * Primary trajectory line. Stroke uses `currentColor`, so wrapping the
 * `<g>` in a tone class paints the line in the metric's identity
 * colour. Secondaries + coloured interval segments land in step 11.
 */
export function ChartLines({
  vm,
  primary,
  scales,
}: {
  vm: DashboardViewModel
  primary: MetricVM
  scales: ChartScales
}) {
  const { xs, ys } = scales
  const points = vm.checkpoints
    .map((c) => ({ index: c.index, value: c.values[primary.id] }))
    .filter(
      (p): p is { index: number; value: number } =>
        typeof p.value === "number",
    )
    .map((p) => ({ x: xs(p.index), y: ys(p.value) }))

  if (points.length < 2) return null

  return (
    <g className={cn(TONE_TEXT[primary.tone])}>
      <path
        d={linePath(points)}
        fill="none"
        stroke="currentColor"
        strokeWidth={1.6}
        strokeOpacity={0.9}
      />
    </g>
  )
}
