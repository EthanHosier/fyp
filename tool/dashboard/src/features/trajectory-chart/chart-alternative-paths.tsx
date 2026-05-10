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
  const [hovered, setHovered] = useState<number | null>(null)

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

        const isSelected =
          selection?.kind === "alternative" && selection.index === alt.index
        const isHovered = hovered === alt.index
        const isActive = isSelected || isHovered

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

        // Hit-target path: same polyline shape, with the two endpoints
        // pulled inward so the fat clickable stroke doesn't overlap
        // the user's checkpoint dots underneath.
        const firstAlt = altPoints[0]
        const lastAlt = altPoints[altPoints.length - 1]
        const fromInset = insetToward(xFrom, ys(yFrom), firstAlt.x, firstAlt.y, ENDPOINT_HIT_INSET_PX)
        const toInset = insetToward(xTo, ys(yTo), lastAlt.x, lastAlt.y, ENDPOINT_HIT_INSET_PX)
        const hitPoints = [
          fromInset,
          ...altPoints.map((p) => ({ x: p.x, y: p.y })),
          toInset,
        ]
        const hitPath = hitPoints
          .map((p, i) => `${i === 0 ? "M" : "L"}${p.x.toFixed(1)},${p.y.toFixed(1)}`)
          .join(" ")

        // Label sits above (or below) the alt's terminal mid-point —
        // last applied step is the one the chip's text refers to.
        const labelAnchor = lastAlt

        return (
          <g
            key={alt.index}
            className={cn("text-brand cursor-pointer")}
            onClick={() => onSelect({ kind: "alternative", index: alt.index })}
            onMouseEnter={() => setHovered(alt.index)}
            onMouseLeave={() => setHovered((h) => (h === alt.index ? null : h))}
          >
            {/* Invisible fat stroke = generous hit target. SVG hit
                detection on a `fill="none"` path only triggers on the
                visible stroke pixels; with a 1.4px dashed line that's
                next to impossible to land on. The transparent 14px
                stroke below catches clicks anywhere near the branch. */}
            <path
              d={hitPath}
              fill="none"
              stroke="transparent"
              strokeWidth={14}
              strokeLinecap="butt"
              pointerEvents="stroke"
            />

            <StubCircle x={xFrom} y={ys(yFrom)} selected={isActive} />
            <StubCircle x={xTo} y={ys(yTo)} selected={isActive} />

            <path
              d={path}
              fill="none"
              stroke="currentColor"
              strokeWidth={isActive ? 2 : 1.4}
              strokeOpacity={isActive ? 0.95 : 0.6}
              strokeDasharray="4 3"
              strokeLinecap="round"
              pointerEvents="none"
            />

            {altPoints.map((p, i) => (
              <circle
                key={i}
                cx={p.x}
                cy={p.y}
                r={isActive ? 3 : 2.2}
                fill="var(--color-bg-1)"
                stroke="currentColor"
                strokeOpacity={isActive ? 0.95 : 0.7}
                strokeWidth={1}
              />
            ))}

            <LabelChip
              x={labelAnchor.x}
              y={labelAnchor.y + offsetSign * 15}
              text={`Alternative: IDE ${alt.label}`}
              active={isActive}
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

