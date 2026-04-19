import type { DashboardViewModel, MetricId } from "@/data/types"
import { linearScale, linearTicks, type LinearScale } from "@/lib/scales"

export type ChartMargin = {
  top: number
  right: number
  bottom: number
  left: number
}

export type ChartScales = {
  xs: LinearScale
  ys: LinearScale
  innerW: number
  innerH: number
  margin: ChartMargin
  yTicks: number[]
  yDomain: [number, number]
}

const DEFAULT_MARGIN: ChartMargin = { top: 24, right: 28, bottom: 32, left: 58 }
const Y_PAD_FRAC = 0.22
const Y_TICK_COUNT = 5

/**
 * Derives the chart scales for a given width/height + primary metric.
 * Y-domain is padded so points don't touch the top/bottom edges; X-domain
 * is the checkpoint index range. Caller owns the ResizeObserver.
 */
export function useChartScales({
  vm,
  primary,
  width,
  height,
  margin = DEFAULT_MARGIN,
}: {
  vm: DashboardViewModel
  primary: MetricId
  width: number
  height: number
  margin?: ChartMargin
}): ChartScales {
  const innerW = Math.max(200, width - margin.left - margin.right)
  const innerH = Math.max(120, height - margin.top - margin.bottom)

  const primaryValues = vm.checkpoints
    .map((c) => c.values[primary])
    .filter((v): v is number => typeof v === "number")

  const yMin = primaryValues.length ? Math.min(...primaryValues) : 0
  const yMax = primaryValues.length ? Math.max(...primaryValues) : 1
  const yPad = (yMax - yMin) * Y_PAD_FRAC || 1
  const yDomain: [number, number] = [yMin - yPad, yMax + yPad]

  const xs = linearScale([0, Math.max(1, vm.checkpoints.length - 1)], [0, innerW])
  const ys = linearScale(yDomain, [innerH, 0])
  const yTicks = linearTicks(yDomain, Y_TICK_COUNT)

  return { xs, ys, innerW, innerH, margin, yTicks, yDomain }
}
