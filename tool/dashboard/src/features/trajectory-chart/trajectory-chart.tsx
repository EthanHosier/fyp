import { useEffect, useRef, useState } from "react"

import type { DashboardViewModel, MetricVM } from "@/data/types"
import { ChartAxes } from "@/features/trajectory-chart/chart-axes"
import { ChartHoverOverlay } from "@/features/trajectory-chart/chart-hover-overlay"
import { ChartIntervalRail, INTERVAL_RAIL_GAP, INTERVAL_RAIL_HEIGHT, INTERVAL_RAIL_OFFSET } from "@/features/trajectory-chart/chart-interval-rail"
import { ChartLegend } from "@/features/trajectory-chart/chart-legend"
import { ChartLines } from "@/features/trajectory-chart/chart-lines"
import { ChartPoints } from "@/features/trajectory-chart/chart-points"
import { ChartRefactoringPoints } from "@/features/trajectory-chart/chart-refactoring-points"
import { ChartToolbar } from "@/features/trajectory-chart/chart-toolbar"
import { useChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { useDashboardStore } from "@/stores/dashboard-store"

const CHART_HEIGHT = 460
const BASE_BOTTOM = 18

function railExtra(count: number): number {
  if (count <= 0) return 0
  return (
    INTERVAL_RAIL_OFFSET +
    count * INTERVAL_RAIL_HEIGHT +
    (count - 1) * INTERVAL_RAIL_GAP +
    6
  )
}

/**
 * Public chart feature — owns the ResizeObserver and composes axes,
 * primary / secondary lines, interval rail, checkpoint points,
 * toolbar and legend.
 */
export function TrajectoryChart({ vm }: { vm: DashboardViewModel }) {
  const hostRef = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(1000)

  useEffect(() => {
    const el = hostRef.current
    if (!el) return
    const ro = new ResizeObserver(([entry]) => {
      setWidth(entry.contentRect.width)
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  const primaryId = useDashboardStore((s) => s.primary)
  const secondaryIds = useDashboardStore((s) => s.secondaries)
  const layers = useDashboardStore((s) => s.layers)
  const selection = useDashboardStore((s) => s.selection)
  const setSelection = useDashboardStore((s) => s.setSelection)

  const primary = vm.metrics.find((m) => m.id === primaryId) ?? vm.metrics[0]
  const secondaries = secondaryIds
    .map((id) => vm.metrics.find((m) => m.id === id))
    .filter((m): m is MetricVM => Boolean(m))

  const railCount =
    (layers.buildIntervals ? 1 : 0) + (layers.testIntervals ? 1 : 0)
  const scales = useChartScales({
    vm,
    primary: primary.id,
    width,
    height: CHART_HEIGHT,
    margin: {
      top: 40,
      right: 28,
      bottom: BASE_BOTTOM + railExtra(railCount),
      left: 58,
    },
  })
  const { margin } = scales

  return (
    <div>
      <ChartToolbar vm={vm} primary={primary} />
      <div
        ref={hostRef}
        className="border-border bg-bg-1 relative rounded-md border px-[10px] pt-[6px] pb-2"
      >
        <svg width={width} height={CHART_HEIGHT} className="block select-none">
          <g transform={`translate(${margin.left},${margin.top})`}>
            <ChartAxes vm={vm} primary={primary} scales={scales} />
            <ChartLines
              vm={vm}
              primary={primary}
              secondaries={secondaries}
              showIntervals={railCount > 0}
              scales={scales}
            />
            <ChartPoints
              vm={vm}
              primary={primary}
              scales={scales}
              selection={selection}
              onSelect={setSelection}
            />
            <ChartRefactoringPoints
              vm={vm}
              primary={primary}
              scales={scales}
              selection={selection}
              onSelect={setSelection}
            />
            {layers.buildIntervals ? (
              <ChartIntervalRail
                vm={vm}
                scales={scales}
                kind="build"
                row={0}
                onSelect={setSelection}
              />
            ) : null}
            {layers.testIntervals ? (
              <ChartIntervalRail
                vm={vm}
                scales={scales}
                kind="tests"
                row={layers.buildIntervals ? 1 : 0}
                onSelect={setSelection}
              />
            ) : null}
            <ChartHoverOverlay
              vm={vm}
              primary={primary}
              secondaries={secondaries}
              scales={scales}
              onSelect={setSelection}
            />
          </g>
        </svg>
      </div>
      <ChartLegend
        primary={primary}
        secondaries={secondaries}
        showIntervals={railCount > 0}
      />
    </div>
  )
}
