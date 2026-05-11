import { Text } from "@/components/text"
import { CommitChip } from "@/components/tone-chip"
import type { CommitMarkerVM, DashboardViewModel, MetricVM } from "@/data/types"
import type { ChartHoverState } from "@/features/trajectory-chart/chart-hover-overlay"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"

// Matches `ChartRefactoringGlyphs`' ToneChip size so the commit chip
// reads as a peer to the X / warn signal glyphs.
export const COMMIT_ICON_SIZE = 14
export const COMMIT_HIT_PAD = 4
// Vertical offset from the trajectory line to the chip's CENTER.
// Matches `ChartRefactoringGlyphs` exactly.
export const COMMIT_CENTER_OFFSET = 17

// Backwards-compat local aliases (kept so the existing code below
// reads cleanly without churn).
const ICON_SIZE = COMMIT_ICON_SIZE
const HOVER_HIT_PAD = COMMIT_HIT_PAD
const CENTER_OFFSET = COMMIT_CENTER_OFFSET

// Hovercard dimensions — mirrors `ChartIntervalRail`'s RailHover.
const TOOLTIP_W = 240
const TOOLTIP_H = 64
const TOOLTIP_OFFSET = 10
const MESSAGE_TRUNC = 80

/**
 * Non-interactive overlay — one Git commit chip per user-side commit,
 * floating just above the user's trajectory line on the active metric.
 *
 * Crucially `pointer-events-none` end-to-end: the chip layer must not
 * intercept mouse events, or it would shadow the underlying
 * `ChartHoverOverlay` capture rect and the checkpoint hover ring would
 * stop appearing. Instead, the hovered commit is derived each render
 * from the shared `hoverState.mx/my` (set by the overlay) — the chip
 * shows a hovercard when those coords fall inside its hit-rect bounds.
 */
export function ChartCommitMarkers({
  vm,
  primary,
  scales,
  hoverState,
}: {
  vm: DashboardViewModel
  primary: MetricVM
  scales: ChartScales
  hoverState: ChartHoverState
}) {
  const { xs, ys, innerW, innerH } = scales
  const [hover] = hoverState

  if (vm.commitMarkers.length === 0) return null

  const anchored = anchoredCommitMarkers(vm, primary, scales)

  // Derive hovered index from shared cursor coords. The mousemove
  // handler in `chartHoverMoveProps` already classifies hover as
  // `{kind: "commit", index}` when the cursor is over a chip, so we
  // can shortcut to that index. We also keep a coords-based fallback
  // for safety (e.g. if a future hover source sets only mx/my).
  let hoveredIndex: number | null = null
  if (hover?.kind === "commit") {
    hoveredIndex = hover.index
  } else if (hover) {
    hoveredIndex = findCommitAt(anchored, hover.mx, hover.my)
  }
  const hoveredAnchor = hoveredIndex !== null ? anchored[hoveredIndex] : null

  return (
    <g className="pointer-events-none">
      {anchored.map((a, i) => (
        <g
          key={a.commit.sha}
          transform={`translate(${a.iconLeftX}, ${a.iconTopY})`}
          opacity={i === hoveredIndex ? 1 : 0.9}
        >
          <CommitChip size={ICON_SIZE} />
        </g>
      ))}
      {hoveredAnchor ? (
        <CommitHovercard
          commit={hoveredAnchor.commit}
          iconCenterX={hoveredAnchor.iconCenterX}
          iconTopY={hoveredAnchor.iconTopY}
          innerW={innerW}
          innerH={innerH}
        />
      ) : null}
    </g>
  )
}

/**
 * Linearly interpolate the user's primary-metric value at chart X
 * coordinate [x]. Clamps to the endpoints when the commit is
 * before/after the trajectory.
 */
function interpolateAtX(
  x: number,
  points: { x: number; v: number }[],
): number | null {
  if (points.length === 0) return null
  if (x <= points[0].x) return points[0].v
  const last = points[points.length - 1]
  if (x >= last.x) return last.v
  for (let i = 1; i < points.length; i++) {
    const l = points[i - 1]
    const r = points[i]
    if (x >= l.x && x <= r.x) {
      const denom = r.x - l.x
      const frac = denom > 0 ? (x - l.x) / denom : 0
      return l.v + frac * (r.v - l.v)
    }
  }
  return last.v
}

function CommitHovercard({
  commit,
  iconCenterX,
  iconTopY,
  innerW,
  innerH,
}: {
  commit: CommitMarkerVM
  iconCenterX: number
  iconTopY: number
  innerW: number
  innerH: number
}) {
  const wantAbove = iconTopY >= TOOLTIP_H + TOOLTIP_OFFSET
  const y = wantAbove
    ? iconTopY - TOOLTIP_H - TOOLTIP_OFFSET
    : Math.min(
        innerH - TOOLTIP_H,
        iconTopY + ICON_SIZE + TOOLTIP_OFFSET,
      )
  const x = clampLeft(iconCenterX, innerW)
  const subject = commit.message.split("\n")[0] || "(empty message)"
  const truncated =
    subject.length > MESSAGE_TRUNC
      ? `${subject.slice(0, MESSAGE_TRUNC - 1)}…`
      : subject
  return (
    <foreignObject
      x={x}
      y={y}
      width={TOOLTIP_W}
      height={TOOLTIP_H}
      overflow="visible"
      pointerEvents="none"
    >
      <div className="bg-bg-1 border-border rounded-md border p-2.5 shadow-md">
        <div className="flex items-center justify-between gap-2">
          <span className="inline-flex items-center gap-1.5">
            <CommitChip size={14} className="shrink-0" />
            <Text variant="mono" tone="fg-2" className="text-[12px]">
              Commit
            </Text>
          </span>
          <Text variant="mono" tone="fg-3" className="text-[11px]">
            {commit.shortSha}
          </Text>
        </div>
        <Text
          variant="bodySm"
          tone="fg-3"
          className="mt-1 line-clamp-2 leading-[1.4]"
        >
          {truncated}
        </Text>
      </div>
    </foreignObject>
  )
}

/** Keep the tooltip card inside the chart's inner viewport. */
function clampLeft(centerX: number, innerW: number): number {
  const half = TOOLTIP_W / 2
  if (centerX + half > innerW) return innerW - TOOLTIP_W
  if (centerX - half < 0) return 0
  return centerX - half
}

/**
 * Resolved chart-coordinate geometry for one commit chip — exported so
 * `chartHoverMoveProps` can hit-test the cursor against the same
 * bounds the chip layer uses to paint.
 */
export type CommitAnchor = {
  commit: CommitMarkerVM
  iconLeftX: number
  iconCenterX: number
  iconTopY: number
}

/**
 * Compute per-commit chip anchors (one entry per visible commit). Same
 * logic the render layer uses; lifted here so hit-testing stays in
 * sync with painting.
 */
export function anchoredCommitMarkers(
  vm: DashboardViewModel,
  primary: MetricVM,
  scales: ChartScales,
): CommitAnchor[] {
  const { xs, ys } = scales
  const linePoints = vm.checkpoints
    .map((c) => ({ x: c.xPos, v: c.values[primary.id] }))
    .filter((p): p is { x: number; v: number } => typeof p.v === "number")
    .sort((a, b) => a.x - b.x)
  const out: CommitAnchor[] = []
  for (const c of vm.commitMarkers) {
    const x = xs(c.xPos)
    const lineV = interpolateAtX(c.xPos, linePoints)
    if (lineV === null) continue
    const cy = ys(lineV)
    const iconTopY = Math.max(0, cy - CENTER_OFFSET - ICON_SIZE / 2)
    out.push({
      commit: c,
      iconLeftX: x - ICON_SIZE / 2,
      iconCenterX: x,
      iconTopY,
    })
  }
  return out
}

/**
 * Returns the index of the commit chip whose hit-rect contains
 * `(mx, my)`, or `null` if the cursor is outside every chip.
 */
export function findCommitAt(
  anchored: CommitAnchor[],
  mx: number,
  my: number,
): number | null {
  for (let i = 0; i < anchored.length; i++) {
    const a = anchored[i]
    const x0 = a.iconLeftX - HOVER_HIT_PAD
    const x1 = a.iconLeftX + ICON_SIZE + HOVER_HIT_PAD
    const y0 = a.iconTopY - HOVER_HIT_PAD
    const y1 = a.iconTopY + ICON_SIZE + HOVER_HIT_PAD
    if (mx >= x0 && mx <= x1 && my >= y0 && my <= y1) return i
  }
  return null
}
