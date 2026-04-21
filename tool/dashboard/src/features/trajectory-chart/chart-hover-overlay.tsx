import { useState } from "react"

import { StatusDot } from "@/components/status-dot"
import { Text } from "@/components/text"
import type {
  CheckpointVM,
  DashboardViewModel,
  IntervalVM,
  MetricVM,
  RefactoringStepVM,
  Selection,
  StatusTone,
} from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"
import { formatDurationShort } from "@/lib/format"
import { TONE_BG, TONE_TEXT } from "@/lib/metric-tone"
import { cn } from "@/lib/utils"

const TOOLTIP_W = 240
const TOOLTIP_H = 110
/** Hit radius around each checkpoint circle (distance in px from the
 * point's centre). Inside → checkpoint hover; outside → interval hover. */
const CHECKPOINT_HIT_PX = 12

type Hover =
  | { kind: "checkpoint"; index: number; mx: number; my: number }
  | { kind: "interval"; index: number; mx: number; my: number }
  | { kind: "refactoring"; index: number; mx: number; my: number }
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
  const { innerW, innerH } = scales

  function handleMove(e: React.MouseEvent<SVGRectElement>) {
    const rect = e.currentTarget.getBoundingClientRect()
    const mx = e.clientX - rect.left
    const my = e.clientY - rect.top
    if (mx < -4 || mx > innerW + 4) {
      setHover(null)
      return
    }
    const base = hoverAt(vm, primary, scales, mx, my)
    setHover(base ? { ...base, mx, my } : null)
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
          mouseX={hover.mx}
          mouseY={hover.my}
        />
      ) : null}
      {hover?.kind === "interval" ? (
        <IntervalCrosshair
          vm={vm}
          interval={vm.intervals[hover.index]}
          primary={primary}
          scales={scales}
          mouseX={hover.mx}
          mouseY={hover.my}
        />
      ) : null}
      {hover?.kind === "refactoring" ? (
        <RefactoringCrosshair
          vm={vm}
          step={vm.refactoringSteps[hover.index]}
          primary={primary}
          scales={scales}
          mouseX={hover.mx}
          mouseY={hover.my}
        />
      ) : null}
    </g>
  )
}

type HoverKind =
  | { kind: "checkpoint"; index: number }
  | { kind: "interval"; index: number }
  | { kind: "refactoring"; index: number }

function hoverAt(
  vm: DashboardViewModel,
  primary: MetricVM,
  scales: ChartScales,
  mx: number,
  my: number,
): HoverKind | null {
  const { xs, ys } = scales
  const tAtMouse = xs.invert(mx)
  // Nearest checkpoint by time, not by index.
  let nearest = 0
  let bestDt = Infinity
  for (const c of vm.checkpoints) {
    const dt = Math.abs(c.tMs - tAtMouse)
    if (dt < bestDt) {
      bestDt = dt
      nearest = c.index
    }
  }
  const nearestCp = vm.checkpoints[nearest]
  const v = nearestCp?.values[primary.id]
  if (typeof v === "number") {
    const dx = mx - xs(nearestCp.tMs)
    const dy = my - ys(v)
    if (Math.hypot(dx, dy) <= CHECKPOINT_HIT_PX) {
      // A checkpoint hit that coincides with a refactoring step is
      // reported as the refactoring — the step is the primary dot,
      // the checkpoint is the sub-point hidden beneath it.
      const step = vm.refactoringSteps.find((s) => s.checkpointIndex === nearest)
      if (step) return { kind: "refactoring", index: step.index }
      return { kind: "checkpoint", index: nearest }
    }
  }
  // Enclosing interval: largest `from` whose tMs <= mouse time.
  const iv = vm.intervals.findLast(
    (x) => vm.checkpoints[x.from].tMs <= tAtMouse,
  )
  if (!iv) return { kind: "checkpoint", index: nearest }
  return { kind: "interval", index: iv.index }
}

function CheckpointCrosshair({
  checkpoint,
  primary,
  secondaries,
  scales,
  mouseX,
  mouseY,
}: {
  checkpoint: CheckpointVM
  primary: MetricVM
  secondaries: MetricVM[]
  scales: ChartScales
  mouseX: number
  mouseY: number
}) {
  const { xs, ys, innerW, innerH } = scales
  const v = checkpoint.values[primary.id]
  if (typeof v !== "number") return null
  const tx = xs(checkpoint.tMs)
  const ty = ys(v)
  const { left, top } = tooltipPos(mouseX, mouseY, innerW, innerH)

  return (
    <g pointerEvents="none">
      <line
        x1={tx}
        x2={tx}
        y1={0}
        y2={innerH}
        className="stroke-current text-fg-3"
        strokeOpacity={0.35}
        strokeDasharray="3 3"
      />
      <circle
        cx={tx}
        cy={ty}
        r={5}
        className="text-fg-3"
        fill="currentColor"
        fillOpacity={0.15}
        stroke="currentColor"
      />
      <foreignObject x={left} y={top} width={TOOLTIP_W} height={TOOLTIP_H} overflow="visible">
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
  mouseX,
  mouseY,
}: {
  vm: DashboardViewModel
  interval: IntervalVM
  primary: MetricVM
  scales: ChartScales
  mouseX: number
  mouseY: number
}) {
  const { xs, innerW, innerH } = scales
  const x0 = xs(vm.checkpoints[interval.from].tMs)
  const x1 = xs(vm.checkpoints[interval.to].tMs)
  const { left, top } = tooltipPos(mouseX, mouseY, innerW, innerH)

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
      <foreignObject x={left} y={top} width={TOOLTIP_W} height={TOOLTIP_H} overflow="visible">
        <IntervalTooltip vm={vm} interval={interval} />
      </foreignObject>
    </g>
  )
}

function RefactoringCrosshair({
  vm,
  step,
  primary,
  scales,
  mouseX,
  mouseY,
}: {
  vm: DashboardViewModel
  step: RefactoringStepVM
  primary: MetricVM
  scales: ChartScales
  mouseX: number
  mouseY: number
}) {
  const { xs, ys, innerW, innerH } = scales
  const cp = vm.checkpoints[step.checkpointIndex]
  const v = cp?.values[primary.id]
  if (typeof v !== "number") return null
  const tx = xs(step.tMs)
  const ty = ys(v)
  const { left, top } = tooltipPos(mouseX, mouseY, innerW, innerH)

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
        r={7}
        className={cn(TONE_TEXT[primary.tone])}
        fill="currentColor"
        fillOpacity={0.2}
        stroke="currentColor"
      />
      <foreignObject x={left} y={top} width={TOOLTIP_W} height={TOOLTIP_H} overflow="visible">
        <RefactoringTooltip step={step} checkpoint={cp} />
      </foreignObject>
    </g>
  )
}

function RefactoringTooltip({
  step,
  checkpoint,
}: {
  step: RefactoringStepVM
  checkpoint: CheckpointVM
}) {
  return (
    <TooltipCard>
      <Text as="div" variant="mono" tone="fg">
        {step.refactoringType}
      </Text>
      <div className="mt-0.5 flex items-center gap-1.5">
        <Text variant="monoTiny" tone="fg-4">
          {step.tLabel}
        </Text>
        <Text variant="monoTiny" tone="fg-4">
          ·
        </Text>
        <StatusDot tone={statusDotTone(checkpoint.status)} />
        <Text variant="monoTiny" tone="fg-3">
          {checkpoint.status}
        </Text>
      </div>
      <Text as="div" variant="bodySm" tone="fg-2" className="mt-1.5 line-clamp-3">
        {step.description}
      </Text>
    </TooltipCard>
  )
}

function tooltipPos(
  mx: number,
  my: number,
  innerW: number,
  innerH: number,
): { left: number; top: number } {
  const OFFSET = 14
  const left =
    mx + OFFSET + TOOLTIP_W > innerW
      ? Math.max(0, mx - OFFSET - TOOLTIP_W)
      : mx + OFFSET
  const top =
    my + OFFSET + TOOLTIP_H > innerH
      ? Math.max(0, my - OFFSET - TOOLTIP_H)
      : my + OFFSET
  return { left, top }
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
        {checkpoint.description}
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
      <div className="flex items-center gap-1.5">
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
