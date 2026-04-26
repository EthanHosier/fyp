import { useLayoutEffect, useRef, useState } from "react"

import type {
  DashboardViewModel,
  MetricVM,
  Selection,
} from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { cn } from "@/lib/utils"

/**
 * Visual offset (px, in chart Y) applied to the alt mid-point and to
 * the bend in the dashed line. Pure cosmetic: separates the alt
 * marker from the user's actual line at the same X so they don't sit
 * on top of each other.
 *
 * Sign is flipped based on the primary metric's "better" direction so
 * the marker always nudges toward the *better* side: a lift (negative
 * px) for higher-is-better metrics, a drop (positive px) for
 * lower-is-better. Does NOT change `alt.altValues` — the detail panel
 * still reports the real measurement.
 */
const ALT_MID_OFFSET_PX = 32

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

  return (
    <g>
      {vm.alternativeTrajectories.map((alt) => {
        const fromCp = vm.checkpoints[alt.fromCheckpointIndex]
        const toCp = vm.checkpoints[alt.toCheckpointIndex]
        if (!fromCp || !toCp) return null

        const yFrom = fromCp.values[primary.id]
        const yTo = toCp.values[primary.id]
        const yAlt = alt.altValues[primary.id]
        if (
          typeof yFrom !== "number" ||
          typeof yTo !== "number" ||
          typeof yAlt !== "number"
        ) {
          return null
        }

        const isSelected =
          selection?.kind === "alternative" && selection.index === alt.index
        const isHovered = hovered === alt.index
        const isActive = isSelected || isHovered

        const xFrom = xs(fromCp.tMs)
        const xTo = xs(toCp.tMs)
        const xMid = (xFrom + xTo) / 2
        // Lift (subtract) for higher-is-better, drop (add) for
        // lower-is-better — px Y axis is inverted vs metric value.
        const offsetSign = primary.better === "higher" ? -1 : 1
        const yMidPx = ys(yAlt) + offsetSign * ALT_MID_OFFSET_PX

        const points = [
          { x: xFrom, y: ys(yFrom) },
          { x: xMid, y: yMidPx },
          { x: xTo, y: ys(yTo) },
        ]
        const path = points
          .map((p, i) => `${i === 0 ? "M" : "L"}${p.x.toFixed(1)},${p.y.toFixed(1)}`)
          .join(" ")

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
              d={path}
              fill="none"
              stroke="transparent"
              strokeWidth={14}
              strokeLinecap="round"
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

            <circle
              cx={xMid}
              cy={yMidPx}
              r={isActive ? 3 : 2.2}
              fill="var(--color-bg-1)"
              stroke="currentColor"
              strokeOpacity={isActive ? 0.95 : 0.7}
              strokeWidth={1}
            />

            <LabelChip
              x={xMid}
              y={yMidPx + offsetSign * 15}
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
    />
  )
}

