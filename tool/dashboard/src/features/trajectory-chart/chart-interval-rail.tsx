import type { DashboardViewModel, Selection, StatusTone } from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { cn } from "@/lib/utils"

export const INTERVAL_RAIL_HEIGHT = 18
export const INTERVAL_RAIL_GAP = 10
export const INTERVAL_RAIL_OFFSET = 40

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
  const { xs, innerH } = scales
  const y =
    innerH +
    INTERVAL_RAIL_OFFSET +
    row * (INTERVAL_RAIL_HEIGHT + INTERVAL_RAIL_GAP)
  const hatchId = `unk-hatch-${kind}`

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
      {mergeRuns(vm, kind).map((run) => {
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
    </g>
  )
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
}

function mergeRuns(vm: DashboardViewModel, kind: IntervalStatusKind): Run[] {
  const runs: Run[] = []
  for (const iv of vm.intervals) {
    const status = kind === "build" ? iv.build : iv.tests
    const last = runs[runs.length - 1]
    if (last && last.status === status) {
      last.toCheckpoint = iv.to
    } else {
      runs.push({
        status,
        firstIntervalIndex: iv.index,
        fromCheckpoint: iv.from,
        toCheckpoint: iv.to,
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
