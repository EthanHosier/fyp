import { useState } from "react"

import type {
  DashboardViewModel,
  MetricVM,
  Selection,
} from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { useDashboardStore } from "@/stores/dashboard-store"
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
  // dot vs continuation dot; `idx` is the segment / step / continuation
  // index inside the alt.
  type HoverTarget = {
    altIndex: number
    kind: "edge" | "dot" | "continuation"
    idx: number
  }
  const [hovered, setHovered] = useState<HoverTarget | null>(null)

  // Whether to render alts whose final process score is below the user's
  // — gated by a layer toggle in the metric rail. Off by default to
  // keep the chart focused on counterfactuals the user could actually
  // benefit from; flipped on for "show me every alt the engine
  // considered" diagnostic mode.
  const showWorseAlternatives = useDashboardStore(
    (s) => s.layers.showWorseAlternatives,
  )

  // Look up each user step's slot xPos by stepIndex so alt step k can
  // align under the user's k-th step in the window (chronological
  // order). For reorder alts whose permutation differs from the user's
  // order, this still lays the alt's progression out left-to-right
  // under the user's step slots — easier to read than backtracking
  // along the alt's actual application order.
  // RefactoringStepVM.index mirrors the server's stepIndex (chronological).
  const xPosByStepIndex = new Map<number, number>()
  for (const s of vm.refactoringSteps) {
    xPosByStepIndex.set(s.index, s.xPos)
  }

  // User's final process score at the trace terminator. The alt is
  // coloured blue (vs grey) when its own final process score beats
  // this — see the per-alt `altWins` calc below.
  const userFinalProcess =
    vm.checkpoints[vm.checkpoints.length - 1]?.processScore

  // When a divergence-point marker is selected, dim every alt that
  // isn't owned by that DP. Alts owned by the selected DP render at
  // full opacity (and remain interactive); others fade so the chart
  // makes it visually obvious which alts demonstrate the picked DP.
  const selectedDp =
    selection?.kind === "divergencePoint"
      ? vm.divergencePoints.find((dp) => dp.id === selection.dpId)
      : undefined
  const dpHighlightedAlts: Set<number> | null = selectedDp
    ? new Set(selectedDp.altIndexes)
    : null

  return (
    <g>
      {vm.alternativeTrajectories.map((alt) => {
        const fromCp = vm.checkpoints[alt.fromCheckpointIndex]
        const toCp = vm.checkpoints[alt.toCheckpointIndex]
        if (!fromCp || !toCp) return null

        const yFrom = fromCp.values[primary.id]
        const yTo = toCp.values[primary.id]
        if (typeof yFrom !== "number" || typeof yTo !== "number") return null

        // Colour criterion: blue iff the alt's final *process score*
        // beats the user's final process score — for an alt with a
        // continuation chain, that's the last continuation step (the
        // End-extended terminator); for an alt without continuation
        // (alt merges at the trace's last real checkpoint), the
        // alt's terminal alt-step process. Metric-agnostic: the cue
        // ("did this alt actually win on process?") is the same
        // regardless of which metric the chart is currently showing.
        const altFinalProcess =
          alt.continuationSteps.length > 0
            ? alt.continuationSteps[alt.continuationSteps.length - 1].processScore
            : alt.altValues.process
        const altWins =
          typeof altFinalProcess === "number" &&
          typeof userFinalProcess === "number" &&
          altFinalProcess >= userFinalProcess
        const isWorse = !altWins
        // Skip worse alts unless the diagnostic toggle is on. They
        // clutter the chart with counterfactuals the user can't act
        // on (the alternative scored lower than their actual path).
        if (isWorse && !showWorseAlternatives) return null

        // Align alt's k-th applied step under the user's k-th window
        // step (chronological — sorted by stepIndex ascending). Y
        // values come from the alt's altCheckpoints in alt-applied
        // order, so the polyline reads as the alt's progression while
        // the X axis stays anchored to the user's slots.
        //
        // Rework alts have no miner stepIndex mapping (they replay
        // raw user commits, not miner-typed refactorings). For those,
        // distribute the alt's K steps evenly along the
        // [fromCp.xPos, toCp.xPos] range so the polyline still spans
        // the alt's range instead of collapsing onto step 0.
        const stepIndexXPositions = alt.steps
          .map((s) => xPosByStepIndex.get(s.stepIndex))
          .filter((x): x is number => typeof x === "number")
          .slice()
          .sort((a, b) => a - b)
        const hasStepIndexAnchors = stepIndexXPositions.length === alt.steps.length
        // Even-distribution fallback used for any alt where collapsing
        // onto user stepIndex xPositions would stack multiple alt
        // checkpoints at the same coordinate. IDE_REPLAY alts that
        // replace a single user step with N specs are the main case:
        // every spec resolves to the same user stepIndex (and thus the
        // same toSha xPos), so the polyline degenerates into a vertical
        // stack at toSha. Spreading the N alt checkpoints evenly across
        // `[fromCp.xPos, toCp.xPos]` makes the progression legible.
        //
        // Distribution: `(i + 1) / (n + 1)` places steps strictly
        // *between* fromCp and toCp — neither endpoint is reused as a
        // slot. With `(i + 1) / n` the last alt step would land at
        // toCp.xPos, which for non-process metrics overlaps the
        // polyline's explicit merge-back vertex at `(xTo, ys(yTo))`
        // and creates a visual stack of the alt's "manual cleanup"
        // dot directly underneath the user's toSha marker.
        // Single-step alts are special-cased to toCp.xPos — those
        // semantically *are* the toSha state (no manual cleanup) so
        // they belong at the toSha xPos.
        const distributedXPositions = (() => {
          const n = alt.steps.length
          if (n === 1) return [toCp.xPos]
          const span = toCp.xPos - fromCp.xPos
          return Array.from({ length: n }, (_, i) => fromCp.xPos + (span * (i + 1)) / (n + 1))
        })()
        // REWORK / HYGIENE alts: anchor each surviving alt checkpoint
        // at the user checkpoint it semantically lands at, looked up
        // via the backend-provided `altCheckpointUserIndexes` map.
        // REWORK uses this after whitespace-only intermediates have
        // been absorbed; HYGIENE alts have empty `stepIndexes` (no
        // miner step) so step-index anchoring doesn't apply either.
        // Falls back to `null` (→ even distribution) when the backend
        // leaves the list empty.
        const userCheckpointAlignedXPositions = (() => {
          if (alt.kind !== "REWORK" && alt.kind !== "HYGIENE") return null
          if (alt.altCheckpointUserIndexes.length !== alt.steps.length) return null
          const xs: number[] = []
          for (const userIdx of alt.altCheckpointUserIndexes) {
            const cp = vm.checkpoints[userIdx]
            if (!cp) return null
            xs.push(cp.xPos)
          }
          return xs
        })()
        const ideStackCollapses =
          alt.kind === "IDE_REPLAY" &&
          hasStepIndexAnchors &&
          new Set(stepIndexXPositions).size < stepIndexXPositions.length
        const userOrderXPositions: number[] =
          userCheckpointAlignedXPositions ??
          (ideStackCollapses || !hasStepIndexAnchors
            ? distributedXPositions
            : stepIndexXPositions)
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

        // Process-score is the only cumulative metric; once paths
        // diverge their cumulative state diverges forever. For every
        // other metric the alt rejoins the user at userToSha because
        // those are state-only (identical once trees converge).
        const isProcess = primary.id === "process"

        // Continuation points (process metric only) — recomputed
        // scores at the user's post-merge checkpoint X positions.
        // `contIndex` is the array index into `alt.continuationSteps`
        // (what the `altContinuation` Selection joins against).
        const continuationPoints: {
          x: number
          y: number
          cpIndex: number
          contIndex: number
        }[] = []
        if (isProcess) {
          alt.continuationSteps.forEach((step, ci) => {
            const cp = vm.checkpoints[step.checkpointIndex]
            if (!cp || typeof step.processScore !== "number") return
            continuationPoints.push({
              x: xs(cp.xPos),
              y: ys(step.processScore),
              cpIndex: step.checkpointIndex,
              contIndex: ci,
            })
          })
        }

        // Clean divergence on process: skip the merge-back vertex at
        // (xTo, ys(yTo)) entirely — the alt's altPoints[last] is
        // already the alt's terminal process Y; the line continues
        // from there through the continuation. For non-process
        // metrics, keep the merge-back vertex (today's behaviour).
        const polyPoints = isProcess
          ? [
              { x: xFrom, y: ys(yFrom) },
              ...altPoints.map((p) => ({ x: p.x, y: p.y })),
              ...continuationPoints.map((p) => ({ x: p.x, y: p.y })),
            ]
          : [
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
        // Process mode: hit targets cover up to the alt's terminal
        // step only — continuation segments are read-only chart
        // chrome and don't dispatch selections.
        const polyAll = isProcess
          ? [
              { x: xFrom, y: ys(yFrom), kind: "from" as const },
              ...altPoints.map((p, i) => ({ x: p.x, y: p.y, kind: "alt" as const, idx: i })),
            ]
          : [
              { x: xFrom, y: ys(yFrom), kind: "from" as const },
              ...altPoints.map((p, i) => ({ x: p.x, y: p.y, kind: "alt" as const, idx: i })),
              { x: xTo, y: ys(yTo), kind: "to" as const },
            ]

        const isDimmedByDp =
          dpHighlightedAlts !== null && !dpHighlightedAlts.has(alt.index)

        return (
          <g
            key={alt.index}
            className={cn(isWorse ? "text-fg-4" : "text-brand")}
            style={isDimmedByDp ? { opacity: 0.2 } : undefined}
          >
            {/* Base dashed alt polyline — pointer events disabled so
                the per-segment hit targets below own click + hover.
                In process mode the alt diverges forever; the stub at
                (xTo, userY) would float disconnected from the polyline
                so we skip it. */}
            <StubCircle x={xFrom} y={ys(yFrom)} selected={isAltSelected} />
            {!isProcess && (
              <StubCircle x={xTo} y={ys(yTo)} selected={isAltSelected} />
            )}
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

            {/* Continuation dots — one per user-checkpoint position
                the alt's recomputed process line passes through after
                the merge. Clickable: opens the alt's process-only
                detail panel (user's state at that sha with the alt's
                process score overlaid). Hover ring matches the
                alt-step dots so the layer reads as one alt object. */}
            {isProcess && continuationPoints.map((p) => {
              const contSelected =
                selection?.kind === "altContinuation" &&
                selection.altIndex === alt.index &&
                selection.continuationIndex === p.contIndex
              const contHovered =
                hovered?.altIndex === alt.index &&
                hovered.kind === "continuation" &&
                hovered.idx === p.contIndex
              const contActive = contSelected || contHovered
              return (
                <g
                  key={`cont-${alt.index}-${p.contIndex}`}
                  className="cursor-pointer"
                  onMouseEnter={() =>
                    setHovered({ altIndex: alt.index, kind: "continuation", idx: p.contIndex })
                  }
                  onMouseLeave={() =>
                    setHovered((h) =>
                      h &&
                      h.altIndex === alt.index &&
                      h.kind === "continuation" &&
                      h.idx === p.contIndex
                        ? null
                        : h,
                    )
                  }
                  onMouseMove={(e) => e.stopPropagation()}
                  onClick={(e) => {
                    e.stopPropagation()
                    onSelect({
                      kind: "altContinuation",
                      altIndex: alt.index,
                      continuationIndex: p.contIndex,
                    })
                  }}
                >
                  <circle cx={p.x} cy={p.y} r={9} fill="transparent" />
                  <circle
                    cx={p.x}
                    cy={p.y}
                    r={contActive ? 3.4 : 2.2}
                    fill="var(--color-bg-1)"
                    stroke="currentColor"
                    strokeOpacity={contActive ? 0.95 : 0.7}
                    strokeWidth={1}
                    strokeDasharray="2 1.5"
                  />
                  {contActive ? (
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

          </g>
        )
      })}
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

