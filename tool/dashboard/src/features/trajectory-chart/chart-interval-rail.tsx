import { useState } from "react"

import { StatusDot } from "@/components/status-dot"
import { Text } from "@/components/text"
import type { DashboardViewModel, Selection, StatusTone } from "@/data/types"
import { statusLabelFor } from "@/features/detail-panel/status-interval-body"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { formatDurationShort } from "@/lib/format"
import { cn } from "@/lib/utils"

export const INTERVAL_RAIL_HEIGHT = 18
export const INTERVAL_RAIL_GAP = 10
export const INTERVAL_RAIL_OFFSET = 40

const TOOLTIP_W = 200
const TOOLTIP_H = 56
const TOOLTIP_OFFSET = 12

type IntervalStatusKind = "build" | "tests"

/**
 * Thin status rail below the X-axis. One rect per interval, coloured
 * by either the build or tests status. Clicks dispatch an interval
 * selection to the dashboard store.
 *
 * Multiple rails stack vertically — caller provides `row` (0-indexed)
 * and we push further down by (railHeight + gap).
 */
export function ChartIntervalRail({
  vm,
  scales,
  kind,
  row = 0,
  onSelect,
}: {
  vm: DashboardViewModel
  scales: ChartScales
  kind: IntervalStatusKind
  row?: number
  onSelect: (s: Selection) => void
}) {
  const { xs, innerW, innerH } = scales
  const y =
    innerH +
    INTERVAL_RAIL_OFFSET +
    row * (INTERVAL_RAIL_HEIGHT + INTERVAL_RAIL_GAP)
  const hatchId = `unk-hatch-${kind}`
  const runs = mergeRuns(vm, kind)
  const [hover, setHover] = useState<{
    runIndex: number
    mx: number
  } | null>(null)
  const hovered = hover ? runs[hover.runIndex] : null

  return (
    <g transform={`translate(0,${y})`}>
      <defs>
        <pattern
          id={hatchId}
          width={6}
          height={6}
          patternUnits="userSpaceOnUse"
          patternTransform="rotate(45)"
        >
          <rect width={6} height={6} className="fill-unknown/20" />
          <rect width={2} height={6} className="fill-unknown/50" />
        </pattern>
      </defs>
      <text
        x={-8}
        y={INTERVAL_RAIL_HEIGHT / 2 + 3}
        textAnchor="end"
        className="fill-fg-4 font-mono"
        fontSize={9.5}
      >
        {kind === "build" ? "BUILD" : "TESTS"}
      </text>
      {runs.map((run, runIndex) => {
        const unknown = run.status === "unknown"
        const x0 = xs(vm.checkpoints[run.fromCheckpoint].tMs)
        const x1 = xs(vm.checkpoints[run.toCheckpoint].tMs)
        return (
          <rect
            key={run.firstIntervalIndex}
            x={x0}
            y={0}
            width={Math.max(0, x1 - x0)}
            height={INTERVAL_RAIL_HEIGHT}
            className={cn(
              !unknown && STATUS_FILL[run.status],
              STATUS_STROKE[run.status],
              "cursor-pointer",
            )}
            fill={unknown ? `url(#${hatchId})` : undefined}
            strokeOpacity={0.4}
            strokeWidth={0.75}
            onMouseMove={(e) => {
              const r = e.currentTarget.getBoundingClientRect()
              const mx = x0 + (e.clientX - r.left)
              setHover({ runIndex, mx })
            }}
            onMouseLeave={() => setHover(null)}
            onClick={() =>
              onSelect({
                kind: "status",
                intervalIndex: run.firstIntervalIndex,
                statusKind: kind,
              })
            }
          />
        )
      })}
      {hovered && hover && (
        <foreignObject
          x={tooltipLeft(hover.mx, innerW)}
          y={-(TOOLTIP_H + TOOLTIP_OFFSET)}
          width={TOOLTIP_W}
          height={TOOLTIP_H}
          overflow="visible"
          pointerEvents="none"
        >
          <RailHover kind={kind} run={hovered} />
        </foreignObject>
      )}
    </g>
  )
}

function tooltipLeft(mx: number, innerW: number): number {
  const half = TOOLTIP_W / 2
  if (mx + half > innerW) return innerW - TOOLTIP_W
  if (mx - half < 0) return 0
  return mx - half
}

function RailHover({ kind, run }: { kind: IntervalStatusKind; run: Run }) {
  const label = statusLabelFor(kind, run.status)
  const kindLabel = kind === "build" ? "Build" : "Tests"
  return (
    <div className="bg-bg-1 border-border rounded-md border p-2.5 shadow-md">
      <div className="flex items-center justify-between gap-3">
        <span className="inline-flex items-center gap-1.5">
          <StatusDot tone={statusDotTone(run.status)} />
          <Text variant="mono" tone="fg-2" className="text-[12px]">
            {kindLabel} {label}
          </Text>
        </span>
        <Text variant="mono" tone="fg-3" className="text-[11px]">
          {formatDurationShort(run.durationMs)}
        </Text>
      </div>
    </div>
  )
}

function statusDotTone(s: StatusTone): "success" | "danger" | "neutral" {
  if (s === "pass") return "success"
  if (s === "fail") return "danger"
  return "neutral"
}

// Collapse consecutive intervals that share the same status into a
// single rect so long green or red stretches render without visible
// seams between their segments. Clicks dispatch the run's first
// interval index — the detail panel keys off that.
type Run = {
  status: StatusTone
  firstIntervalIndex: number
  fromCheckpoint: number
  toCheckpoint: number
  durationMs: number
}

function mergeRuns(vm: DashboardViewModel, kind: IntervalStatusKind): Run[] {
  const runs: Run[] = []
  for (const iv of vm.intervals) {
    const status = kind === "build" ? iv.build : iv.tests
    const last = runs[runs.length - 1]
    if (last && last.status === status) {
      last.toCheckpoint = iv.to
      last.durationMs += iv.durationMs
    } else {
      runs.push({
        status,
        firstIntervalIndex: iv.index,
        fromCheckpoint: iv.from,
        toCheckpoint: iv.to,
        durationMs: iv.durationMs,
      })
    }
  }
  return runs
}

const STATUS_FILL: Record<StatusTone, string> = {
  pass: "fill-good/20",
  fail: "fill-bad/25",
  unknown: "fill-unknown/20",
}

const STATUS_STROKE: Record<StatusTone, string> = {
  pass: "stroke-good",
  fail: "stroke-bad",
  unknown: "stroke-unknown",
}
