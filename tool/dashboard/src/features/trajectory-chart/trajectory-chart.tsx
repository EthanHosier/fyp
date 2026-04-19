import { useEffect, useRef, useState } from "react"

import type { DashboardViewModel } from "@/data/types"
import { ChartAxes } from "@/features/trajectory-chart/chart-axes"
import { ChartLegend } from "@/features/trajectory-chart/chart-legend"
import { ChartLines } from "@/features/trajectory-chart/chart-lines"
import { ChartPoints } from "@/features/trajectory-chart/chart-points"
import { ChartToolbar } from "@/features/trajectory-chart/chart-toolbar"
import { useChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { useDashboardStore } from "@/stores/dashboard-store"

const CHART_HEIGHT = 360

/**
 * Public chart feature — owns the ResizeObserver and composes axes,
 * primary line, checkpoint points, toolbar and legend. Secondaries,
 * the build/test interval rail, and hover tooltip land in steps 11–12.
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
  const selection = useDashboardStore((s) => s.selection)
  const setSelection = useDashboardStore((s) => s.setSelection)

  const primary = vm.metrics.find((m) => m.id === primaryId) ?? vm.metrics[0]
  const scales = useChartScales({
    vm,
    primary: primary.id,
    width,
    height: CHART_HEIGHT,
  })
  const { margin, innerW, innerH } = scales

  return (
    <div>
      <ChartToolbar vm={vm} primary={primary} />
      <div
        ref={hostRef}
        className="border-border bg-bg-1 relative rounded-md border px-[10px] pt-[6px] pb-2"
      >
        <svg
          width={width}
          height={CHART_HEIGHT}
          className="block select-none"
        >
          <g transform={`translate(${margin.left},${margin.top})`}>
            <ChartAxes vm={vm} primary={primary} scales={scales} />
            <ChartLines vm={vm} primary={primary} scales={scales} />
            <ChartPoints
              vm={vm}
              primary={primary}
              scales={scales}
              selection={selection}
              onSelect={setSelection}
            />
            <rect
              x={0}
              y={0}
              width={innerW}
              height={innerH}
              fill="transparent"
              pointerEvents="none"
            />
          </g>
        </svg>
      </div>
      <ChartLegend primary={primary} />
    </div>
  )
}
