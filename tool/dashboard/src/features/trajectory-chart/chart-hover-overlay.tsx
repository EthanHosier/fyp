import { useState } from "react"

import { StatusDot } from "@/components/status-dot"
import { Text } from "@/components/text"
import type {
  CheckpointVM,
  DashboardViewModel,
  MetricVM,
  Selection,
  StatusTone,
} from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { TONE_BG, TONE_TEXT } from "@/lib/metric-tone"
import { cn } from "@/lib/utils"

const TOOLTIP_W = 240
const TOOLTIP_H = 140

/**
 * Transparent hit-rect over the plot area. Tracks mouse position,
 * snaps to the nearest checkpoint index, and renders a vertical
 * crosshair + HTML tooltip (inside a foreignObject) with the current
 * values. Clicking anywhere dispatches a checkpoint selection, which
 * is why this layer sits above chart-points.
 */
export function ChartHoverOverlay({
  vm,
  primary,
  secondaries,
  scales,
  onSelect,
}: {
  vm: DashboardViewModel
  primary: MetricVM
  secondaries: MetricVM[]
  scales: ChartScales
  onSelect: (s: Selection) => void
}) {
  const [hoverIdx, setHoverIdx] = useState<number | null>(null)
  const { xs, ys, innerW, innerH } = scales

  function handleMove(e: React.MouseEvent<SVGRectElement>) {
    const rect = e.currentTarget.getBoundingClientRect()
    const mx = e.clientX - rect.left
    if (mx < -4 || mx > innerW + 4) {
      setHoverIdx(null)
      return
    }
    const raw = xs.invert(mx)
    const idx = Math.max(
      0,
      Math.min(vm.checkpoints.length - 1, Math.round(raw)),
    )
    setHoverIdx(idx)
  }

  const hover = hoverIdx != null ? vm.checkpoints[hoverIdx] : null
  const hoverValue = hover ? hover.values[primary.id] : undefined

  return (
    <g>
      <rect
        x={0}
        y={0}
        width={innerW}
        height={innerH}
        fill="transparent"
        className="cursor-pointer"
        onMouseMove={handleMove}
        onMouseLeave={() => setHoverIdx(null)}
        onClick={() => {
          if (hoverIdx != null) onSelect({ kind: "checkpoint", index: hoverIdx })
        }}
      />
      {hover && typeof hoverValue === "number" ? (
        <Crosshair
          checkpoint={hover}
          primary={primary}
          primaryValue={hoverValue}
          secondaries={secondaries}
          scales={scales}
        />
      ) : null}
    </g>
  )
}

function Crosshair({
  checkpoint,
  primary,
  primaryValue,
  secondaries,
  scales,
}: {
  checkpoint: CheckpointVM
  primary: MetricVM
  primaryValue: number
  secondaries: MetricVM[]
  scales: ChartScales
}) {
  const { xs, ys, innerW, innerH } = scales
  const tx = xs(checkpoint.index)
  const ty = ys(primaryValue)
  const tLeft = tx + TOOLTIP_W + 16 > innerW ? tx - TOOLTIP_W - 12 : tx + 12
  const tTop = Math.max(4, ty - 70)

  return (
    <g pointerEvents="none">
      <line
        x1={tx}
        x2={tx}
        y1={0}
        y2={innerH}
        className={cn("stroke-current", TONE_TEXT[primary.tone])}
        strokeOpacity={0.35}
        strokeDasharray="3 3"
      />
      <circle
        cx={tx}
        cy={ty}
        r={5}
        className={cn(TONE_TEXT[primary.tone])}
        fill="currentColor"
        fillOpacity={0.15}
        stroke="currentColor"
      />
      <foreignObject x={tLeft} y={tTop} width={TOOLTIP_W} height={TOOLTIP_H}>
        <TooltipCard
          checkpoint={checkpoint}
          primary={primary}
          primaryValue={primaryValue}
          secondaries={secondaries}
        />
      </foreignObject>
    </g>
  )
}

function TooltipCard({
  checkpoint,
  primary,
  primaryValue,
  secondaries,
}: {
  checkpoint: CheckpointVM
  primary: MetricVM
  primaryValue: number
  secondaries: MetricVM[]
}) {
  return (
    <div
      className="bg-bg-2 border-border-strong rounded-md border px-2.5 py-2 shadow-lg"
      xmlns="http://www.w3.org/1999/xhtml"
    >
      <Text as="div" variant="mono" tone="fg">
        {checkpoint.label}
      </Text>
      <div className="mt-0.5 flex items-center gap-1.5">
        <Text variant="monoTiny" tone="fg-4">
          {checkpoint.tLabel}
        </Text>
        <Text variant="monoTiny" tone="fg-4">
          ·
        </Text>
        <StatusDot tone={statusDotTone(checkpoint.status)} />
        <Text variant="monoTiny" tone="fg-3">
          {checkpoint.status}
        </Text>
      </div>
      <div className="mt-1.5 grid grid-cols-[1fr_auto] gap-x-3 gap-y-0.5">
        <MetricLabel metric={primary} />
        <Text variant="mono" tone="fg">{primaryValue}</Text>
        {secondaries.map((m) => {
          const v = checkpoint.values[m.id]
          if (typeof v !== "number") return null
          return (
            <MetricLabelRow key={m.id} metric={m} value={v} />
          )
        })}
      </div>
    </div>
  )
}

function MetricLabelRow({ metric, value }: { metric: MetricVM; value: number }) {
  return (
    <>
      <MetricLabel metric={metric} />
      <Text variant="mono" tone="fg-2">{value}</Text>
    </>
  )
}

function MetricLabel({ metric }: { metric: MetricVM }) {
  return (
    <Text as="span" variant="bodySm" tone="inherit" className="inline-flex items-center gap-1.5">
      <span className={cn("inline-block size-2 rounded-full", TONE_BG[metric.tone])} />
      <span className="text-fg-2">{metric.label}</span>
    </Text>
  )
}

function statusDotTone(status: StatusTone): "success" | "danger" | "neutral" {
  if (status === "pass") return "success"
  if (status === "fail") return "danger"
  return "neutral"
}
