import { useState } from "react"

import { StatusDot } from "@/components/status-dot"
import { Text } from "@/components/text"
import type {
  CheckpointVM,
  DashboardViewModel,
  IntervalVM,
  MetricVM,
  Selection,
  StatusTone,
} from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { formatDurationShort } from "@/lib/format"
import { TONE_BG, TONE_TEXT } from "@/lib/metric-tone"
import { cn } from "@/lib/utils"

const TOOLTIP_W = 240
const TOOLTIP_H = 140
/** Snap radius around each checkpoint x — inside this band we hover
 * the checkpoint; outside it we hover the enclosing interval. */
const CHECKPOINT_SNAP_PX = 10

type Hover =
  | { kind: "checkpoint"; index: number }
  | { kind: "interval"; index: number }
  | null

/**
 * Transparent hit-rect over the plot area. Mouse position picks either
 * the nearest checkpoint (when close) or the enclosing interval, then
 * renders a crosshair + HTML tooltip (inside a foreignObject). Click
 * dispatches the matching selection.
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
  const [hover, setHover] = useState<Hover>(null)
  const { xs, innerW, innerH } = scales

  function handleMove(e: React.MouseEvent<SVGRectElement>) {
    const rect = e.currentTarget.getBoundingClientRect()
    const mx = e.clientX - rect.left
    if (mx < -4 || mx > innerW + 4) {
      setHover(null)
      return
    }
    setHover(hoverAt(vm, scales, mx))
  }

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
        onMouseLeave={() => setHover(null)}
        onClick={() => {
          if (hover) onSelect(hover)
        }}
      />
      {hover?.kind === "checkpoint" ? (
        <CheckpointCrosshair
          checkpoint={vm.checkpoints[hover.index]}
          primary={primary}
          secondaries={secondaries}
          scales={scales}
        />
      ) : null}
      {hover?.kind === "interval" ? (
        <IntervalCrosshair
          vm={vm}
          interval={vm.intervals[hover.index]}
          primary={primary}
          scales={scales}
        />
      ) : null}
    </g>
  )
}

function hoverAt(
  vm: DashboardViewModel,
  scales: ChartScales,
  mx: number,
): Hover {
  const { xs } = scales
  const raw = xs.invert(mx)
  const nearest = Math.max(
    0,
    Math.min(vm.checkpoints.length - 1, Math.round(raw)),
  )
  const nearestX = xs(nearest)
  if (Math.abs(mx - nearestX) <= CHECKPOINT_SNAP_PX) {
    return { kind: "checkpoint", index: nearest }
  }
  // Otherwise find the interval whose [fromX, toX] contains mx.
  const floor = Math.max(0, Math.min(vm.intervals.length - 1, Math.floor(raw)))
  const iv = vm.intervals[floor]
  if (!iv) return { kind: "checkpoint", index: nearest }
  return { kind: "interval", index: iv.index }
}

function CheckpointCrosshair({
  checkpoint,
  primary,
  secondaries,
  scales,
}: {
  checkpoint: CheckpointVM
  primary: MetricVM
  secondaries: MetricVM[]
  scales: ChartScales
}) {
  const { xs, ys, innerW, innerH } = scales
  const v = checkpoint.values[primary.id]
  if (typeof v !== "number") return null
  const tx = xs(checkpoint.index)
  const ty = ys(v)
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
        <CheckpointTooltip
          checkpoint={checkpoint}
          primary={primary}
          primaryValue={v}
          secondaries={secondaries}
        />
      </foreignObject>
    </g>
  )
}

function IntervalCrosshair({
  vm,
  interval,
  primary,
  scales,
}: {
  vm: DashboardViewModel
  interval: IntervalVM
  primary: MetricVM
  scales: ChartScales
}) {
  const { xs, innerW, innerH } = scales
  const x0 = xs(interval.from)
  const x1 = xs(interval.to)
  const midX = (x0 + x1) / 2
  const tLeft = midX + TOOLTIP_W / 2 > innerW ? innerW - TOOLTIP_W : Math.max(0, midX - TOOLTIP_W / 2)

  return (
    <g pointerEvents="none">
      <rect
        x={x0}
        y={0}
        width={Math.max(0, x1 - x0)}
        height={innerH}
        className={cn("fill-current", TONE_TEXT[primary.tone])}
        fillOpacity={0.08}
      />
      <line
        x1={x0}
        x2={x0}
        y1={0}
        y2={innerH}
        className="stroke-border-strong"
        strokeOpacity={0.5}
      />
      <line
        x1={x1}
        x2={x1}
        y1={0}
        y2={innerH}
        className="stroke-border-strong"
        strokeOpacity={0.5}
      />
      <foreignObject x={tLeft} y={16} width={TOOLTIP_W} height={TOOLTIP_H}>
        <IntervalTooltip vm={vm} interval={interval} />
      </foreignObject>
    </g>
  )
}

function CheckpointTooltip({
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
    <TooltipCard>
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
        <Text variant="mono" tone="fg">
          {primaryValue}
        </Text>
        {secondaries.map((m) => {
          const v = checkpoint.values[m.id]
          if (typeof v !== "number") return null
          return (
            <MetricLabelRow key={m.id} metric={m} value={v} />
          )
        })}
      </div>
    </TooltipCard>
  )
}

function IntervalTooltip({
  vm,
  interval,
}: {
  vm: DashboardViewModel
  interval: IntervalVM
}) {
  const from = vm.checkpoints[interval.from]
  const to = vm.checkpoints[interval.to]
  return (
    <TooltipCard>
      <Text as="div" variant="mono" tone="fg">
        {from.label} → {to.label}
      </Text>
      <div className="mt-0.5 flex items-center gap-1.5">
        <Text variant="monoTiny" tone="fg-4">
          {formatDurationShort(interval.durationMs)}
        </Text>
        <Text variant="monoTiny" tone="fg-4">
          ·
        </Text>
        <StatusDot tone={statusDotTone(interval.status)} />
        <Text variant="monoTiny" tone="fg-3">
          build {interval.build} · tests {interval.tests}
        </Text>
      </div>
      <div className="mt-1.5 grid grid-cols-[1fr_auto] gap-x-3 gap-y-0.5">
        {vm.metrics.map((m) => {
          const a = from.values[m.id]
          const b = to.values[m.id]
          if (typeof a !== "number" || typeof b !== "number") return null
          const diff = b - a
          if (diff === 0) return null
          const improved =
            m.better === "lower" ? diff < 0 : diff > 0
          const decimals = m.unit === "%" ? 1 : 0
          return (
            <MetricLabelRow
              key={m.id}
              metric={m}
              value={
                <span className={improved ? "text-good" : "text-bad"}>
                  {diff > 0 ? "+" : ""}
                  {diff.toFixed(decimals)}
                </span>
              }
            />
          )
        })}
      </div>
    </TooltipCard>
  )
}

function TooltipCard({ children }: { children: React.ReactNode }) {
  return (
    <div
      className="bg-bg-2 border-border-strong rounded-md border px-2.5 py-2 shadow-lg"
      xmlns="http://www.w3.org/1999/xhtml"
    >
      {children}
    </div>
  )
}

function MetricLabelRow({
  metric,
  value,
}: {
  metric: MetricVM
  value: React.ReactNode
}) {
  return (
    <>
      <MetricLabel metric={metric} />
      <Text as="div" variant="mono" tone="fg-2">
        {value}
      </Text>
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
