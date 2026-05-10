import { useLayoutEffect, useRef, useState } from "react"

import type {
  DashboardViewModel,
  MetricVM,
  Selection,
} from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { cn } from "@/lib/utils"

/**
 * Distance (px) the hit-target fat stroke is pulled inward from each
 * stub circle. Clicks within this end-zone fall through to the
 * underlying ChartPoints checkpoint dot, so picking the endpoint
 * opens the checkpoint detail panel rather than the alt-path one —
 * only clicks on the edge itself open the alternative.
 */
const ENDPOINT_HIT_INSET_PX = 10

/**
 * Renders synthesised IDE-driven alternative paths as dashed branches
 * that leave the user's actual trajectory at the alt's `fromCheckpoint`,
 * pass through the alt checkpoint's primary-metric value at a midpoint
 * X, and rejoin at the user's `toCheckpoint`. Style mirrors the
 * reference dashboard — open dashed stub circles at the on-ramp / off-
 * ramp, dashed connecting line, filled mid-dot, label chip.
 *
 * All paths share a single brand-blue tone (`text-brand-2`) — same hue
 * the reference uses for IDE-refactor annotations. Outcome-derived
 * colouring (better / worse / neutral) was tried first but cluttered
 * the chart with chrome that fluctuates as the user flips primaries;
 * a single hue keeps the alt layer scannable and lets the detail
 * panel carry the "is this actually an improvement?" comparison.
 */
export function ChartAlternativePaths({
  vm,
  primary,
  scales,
  selection,
  onSelect,
}: {
  vm: DashboardViewModel
  primary: MetricVM
  scales: ChartScales
  selection: Selection
  onSelect: (s: Selection) => void
}) {
  const { xs, ys } = scales
  // Per-element hover so a single edge / dot lights up rather than the
  // whole alt path going active together. `kind` distinguishes edge vs
  // dot; `idx` is the segment / step index inside the alt.
  type HoverTarget = { altIndex: number; kind: "edge" | "dot"; idx: number }
  const [hovered, setHovered] = useState<HoverTarget | null>(null)

  // Look up each user step's slot xPos by stepIndex so alt step k can
  // align under the user's k-th step in the window (chronological
  // order). For reorder alts whose permutation differs from the user's
  // order, this still lays the alt's progression out left-to-right
  // under the user's step slots — easier to read than backtracking
  // along the alt's actual application order.
  // RefactoringStepVM.index mirrors the server's stepIndex (chronological).
  const xPosByStepIndex = new Map<number, number>()
  for (const s of vm.refactoringSteps) xPosByStepIndex.set(s.index, s.xPos)

  return (
    <g>
      {vm.alternativeTrajectories.map((alt) => {
        const fromCp = vm.checkpoints[alt.fromCheckpointIndex]
        const toCp = vm.checkpoints[alt.toCheckpointIndex]
        if (!fromCp || !toCp) return null

        const yFrom = fromCp.values[primary.id]
        const yTo = toCp.values[primary.id]
        if (typeof yFrom !== "number" || typeof yTo !== "number") return null

        // Align alt's k-th applied step under the user's k-th window
        // step (chronological — sorted by stepIndex ascending). Y
        // values come from the alt's altCheckpoints in alt-applied
        // order, so the polyline reads as the alt's progression while
        // the X axis stays anchored to the user's slots.
        const userOrderXPositions = alt.steps
          .map((s) => xPosByStepIndex.get(s.stepIndex))
          .filter((x): x is number => typeof x === "number")
          .slice()
          .sort((a, b) => a - b)
        const altPoints: { x: number; y: number; vm: typeof alt.steps[number] }[] = []
        for (let k = 0; k < alt.steps.length; k++) {
          const slotX = userOrderXPositions[k]
          const stepVm = alt.steps[k]
          const yVal = stepVm.altValues[primary.id]
          if (slotX === undefined || typeof yVal !== "number") continue
          altPoints.push({ x: xs(slotX), y: ys(yVal), vm: stepVm })
        }
        if (altPoints.length === 0) return null

        // Whole-alt active state is only used for the explicit
        // `alternative` selection (not driven by per-element hover any
        // more). Per-element hover effects are scoped below.
        const isAltSelected =
          selection?.kind === "alternative" && selection.index === alt.index

        const xFrom = xs(fromCp.xPos)
        const xTo = xs(toCp.xPos)
        // Label chip nudge — toward the "better" side of the metric so
        // it doesn't overlap the user's line. Doesn't change any plot
        // coordinates.
        const offsetSign = primary.better === "higher" ? -1 : 1

        const polyPoints = [
          { x: xFrom, y: ys(yFrom) },
          ...altPoints.map((p) => ({ x: p.x, y: p.y })),
          { x: xTo, y: ys(yTo) },
        ]
        const path = polyPoints
          .map((p, i) => `${i === 0 ? "M" : "L"}${p.x.toFixed(1)},${p.y.toFixed(1)}`)
          .join(" ")

        // Per-segment hit targets so each edge of the alt path can
        // dispatch its own altInterval selection. Endpoints inset
        // toward the segment's midpoint so the fat clickable stroke
        // doesn't overlap the user's checkpoint dots / alt step dots.
        const lastAlt = altPoints[altPoints.length - 1]
        const polyAll = [
          { x: xFrom, y: ys(yFrom), kind: "from" as const },
          ...altPoints.map((p, i) => ({ x: p.x, y: p.y, kind: "alt" as const, idx: i })),
          { x: xTo, y: ys(yTo), kind: "to" as const },
        ]

        // Label sits above (or below) the alt's terminal mid-point —
        // last applied step is the one the chip's text refers to.
        const labelAnchor = lastAlt

        return (
          <g key={alt.index} className={cn("text-brand")}>
            {/* Base dashed alt polyline — pointer events disabled so
                the per-segment hit targets below own click + hover. */}
            <StubCircle x={xFrom} y={ys(yFrom)} selected={isAltSelected} />
            <StubCircle x={xTo} y={ys(yTo)} selected={isAltSelected} />
            <path
              d={path}
              fill="none"
              stroke="currentColor"
              strokeWidth={isAltSelected ? 2 : 1.4}
              strokeOpacity={isAltSelected ? 0.95 : 0.6}
              strokeDasharray="4 3"
              strokeLinecap="round"
              pointerEvents="none"
            />

            {/* Per-segment hit targets + visible hover/selected
                highlight. Each segment k dispatches `altInterval` for
                segIdx = k. Hovering a segment thickens just that
                segment's stroke instead of brightening the whole
                path. */}
            {polyAll.slice(0, -1).map((from, k) => {
              const to = polyAll[k + 1]
              const fromInset = insetToward(from.x, from.y, to.x, to.y, ENDPOINT_HIT_INSET_PX)
              const toInset = insetToward(to.x, to.y, from.x, from.y, ENDPOINT_HIT_INSET_PX)
              const segPath = `M${fromInset.x.toFixed(1)},${fromInset.y.toFixed(1)} L${toInset.x.toFixed(1)},${toInset.y.toFixed(1)}`
              const segSelected =
                selection?.kind === "altInterval" &&
                selection.altIndex === alt.index &&
                selection.segmentIndex === k
              const segHovered =
                hovered?.altIndex === alt.index &&
                hovered.kind === "edge" &&
                hovered.idx === k
              const segActive = segSelected || segHovered
              return (
                <g key={`seg-${k}`}>
                  {/* Visible accent rendered only on hover/selected so
                      neighbouring segments stay at base opacity. */}
                  {segActive ? (
                    <path
                      d={segPath}
                      fill="none"
                      stroke="currentColor"
                      strokeWidth={2.2}
                      strokeOpacity={0.95}
                      strokeDasharray="4 3"
                      strokeLinecap="round"
                      pointerEvents="none"
                    />
                  ) : null}
                  <path
                    d={segPath}
                    fill="none"
                    stroke="transparent"
                    strokeWidth={14}
                    strokeLinecap="butt"
                    pointerEvents="stroke"
                    className="cursor-pointer"
                    onMouseEnter={() => setHovered({ altIndex: alt.index, kind: "edge", idx: k })}
                    onMouseLeave={() =>
                      setHovered((h) =>
                        h && h.altIndex === alt.index && h.kind === "edge" && h.idx === k ? null : h,
                      )
                    }
                    // Stop mousemove from reaching the capture rect
                    // beneath — otherwise it computes a user-trajectory
                    // hover (nearest checkpoint / interval) and the
                    // overlay paints a second crosshair on top of the
                    // alt segment we're already highlighting.
                    onMouseMove={(e) => e.stopPropagation()}
                    onClick={(e) => {
                      e.stopPropagation()
                      onSelect({ kind: "altInterval", altIndex: alt.index, segmentIndex: k })
                    }}
                  />
                </g>
              )
            })}

            {/* Per-step dots — each clickable to open an
                `altCheckpoint` selection. Hover expands just that dot
                + adds a soft outer ring (matching the user-checkpoint
                hover treatment). Fat invisible halo gives a forgiving
                hit target without making the visible dot bigger. */}
            {altPoints.map((p, i) => {
              const stepSelected =
                selection?.kind === "altCheckpoint" &&
                selection.altIndex === alt.index &&
                selection.stepIndex === i
              const stepHovered =
                hovered?.altIndex === alt.index &&
                hovered.kind === "dot" &&
                hovered.idx === i
              const dotActive = stepSelected || stepHovered
              return (
                <g
                  key={i}
                  className="cursor-pointer"
                  onMouseEnter={() => setHovered({ altIndex: alt.index, kind: "dot", idx: i })}
                  onMouseLeave={() =>
                    setHovered((h) =>
                      h && h.altIndex === alt.index && h.kind === "dot" && h.idx === i ? null : h,
                    )
                  }
                  onMouseMove={(e) => e.stopPropagation()}
                  onClick={(e) => {
                    e.stopPropagation()
                    onSelect({ kind: "altCheckpoint", altIndex: alt.index, stepIndex: i })
                  }}
                >
                  <circle cx={p.x} cy={p.y} r={9} fill="transparent" />
                  <circle
                    cx={p.x}
                    cy={p.y}
                    r={dotActive ? 3.4 : 2.2}
                    fill="var(--color-bg-1)"
                    stroke="currentColor"
                    strokeOpacity={dotActive ? 0.95 : 0.7}
                    strokeWidth={1}
                  />
                  {dotActive ? (
                    <circle
                      cx={p.x}
                      cy={p.y}
                      r={6}
                      fill="none"
                      stroke="currentColor"
                      strokeOpacity={0.55}
                      strokeWidth={1.2}
                    />
                  ) : null}
                </g>
              )
            })}

            <LabelChip
              x={labelAnchor.x}
              y={labelAnchor.y + offsetSign * 15}
              text={`Alternative: IDE ${alt.label}`}
              active={isAltSelected}
            />
          </g>
        )
      })}
    </g>
  )
}

/**
 * Renders a chip whose rect width snaps to the actual rendered text
 * extent. The reference dashboard hard-codes `label.length * 5.4 + 8`,
 * which assumes a monospace metric and stops being accurate the moment
 * the runtime falls back to a non-mono font (we hit this for
 * "Alternative: …" prefixes — descenders + colon misalign the box).
 * Measuring via `getBBox` after layout is the only way to be sure.
 */
function LabelChip({
  x,
  y,
  text,
  active,
}: {
  x: number
  y: number
  text: string
  active: boolean
}) {
  const textRef = useRef<SVGTextElement | null>(null)
  const [width, setWidth] = useState<number | null>(null)

  useLayoutEffect(() => {
    if (textRef.current) {
      const bb = textRef.current.getBBox()
      // 8px horizontal padding, 4 each side — matches reference proportions.
      setWidth(bb.width + 8)
    }
  }, [text])

  return (
    <g transform={`translate(${x},${y})`}>
      <rect
        x={-(width ?? 0) / 2}
        y={-10}
        width={width ?? 0}
        height={14}
        fill="var(--color-bg-1)"
        stroke="currentColor"
        strokeOpacity={active ? 0.95 * 0.7 : 0.55 * 0.7}
        rx={3}
        ry={3}
      />
      <text
        ref={textRef}
        x={0}
        y={1}
        textAnchor="middle"
        fontSize={10.5}
        fontFamily="var(--mono)"
        fill="currentColor"
        fillOpacity={active ? 0.95 : 0.55}
      >
        {text}
      </text>
    </g>
  )
}

function StubCircle({
  x,
  y,
  selected,
}: {
  x: number
  y: number
  selected: boolean
}) {
  return (
    <circle
      cx={x}
      cy={y}
      r={4.5}
      fill="none"
      stroke="currentColor"
      strokeOpacity={selected ? 0.9 : 0.5}
      strokeDasharray="1.5 1.5"
      // Visual-only — clicks fall through to the checkpoint dot
      // underneath so endpoint selection routes to checkpoint detail.
      pointerEvents="none"
    />
  )
}

/**
 * Returns a point at distance [d] along the (ax,ay)→(bx,by) ray.
 * Used to inset the hit-target path away from the alt's endpoints
 * so endpoint clicks reach the checkpoint dot underneath.
 */
function insetToward(
  ax: number,
  ay: number,
  bx: number,
  by: number,
  d: number,
): { x: number; y: number } {
  const dx = bx - ax
  const dy = by - ay
  const len = Math.hypot(dx, dy)
  if (len === 0) return { x: ax, y: ay }
  return { x: ax + (dx / len) * d, y: ay + (dy / len) * d }
}

