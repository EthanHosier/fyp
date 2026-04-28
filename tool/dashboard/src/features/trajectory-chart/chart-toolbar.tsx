import { ArrowDownIcon, ArrowUpIcon } from "lucide-react"

import { Text } from "@/components/text"
import { METRIC_DESCRIPTORS } from "@/data/metric-descriptors"
import type { DashboardViewModel, MetricVM } from "@/data/types"
import { MetricExplainer } from "@/features/trajectory-chart/metric-explainer"
import { TONE_TEXT } from "@/lib/metric-tone"

/**
 * Header strip above the chart canvas. Mirrors the reference:
 * eyebrow "TRAJECTORY · {metric}", display title "{label} over N
 * checkpoints", and a mono caption showing the first → last value.
 */
export function ChartToolbar({
  vm,
  primary,
}: {
  vm: DashboardViewModel
  primary: MetricVM
}) {
  const values = vm.checkpoints
    .map((c) => c.values[primary.id])
    .filter((v): v is number => typeof v === "number")
  // Match MetricTile's display precision: 1dp for `%`, 0dp otherwise.
  // Without this, raw scores like 75.00555555555559 surface in the title.
  const dp = primary.unit === "%" ? 1 : 0
  const first = values[0]?.toFixed(dp)
  const last = values[values.length - 1]?.toFixed(dp)
  const desc = METRIC_DESCRIPTORS[primary.id]

  const Arrow = primary.better === "higher" ? ArrowUpIcon : ArrowDownIcon

  return (
    <div className="px-1 pt-1 pb-[10px]">
      <Text as="div" variant="eyebrow" tone="fg-4">
        Trajectory · {primary.label}
      </Text>
      <Text as="div" variant="display" tone="fg" className="mt-0.5">
        {primary.label} over {vm.checkpoints.length} checkpoints
        {first !== undefined && last !== undefined ? (
          <Text variant="mono" tone="fg-4" className="ml-2.5 font-normal">
            {first} → {last} {primary.unit}
          </Text>
        ) : null}
      </Text>
      <div className="mt-1 flex flex-wrap items-baseline gap-3">
        <div className="flex flex-1 flex-wrap items-baseline gap-x-2 gap-y-0.5">
          <Text as="span" variant="body" tone="fg-3">
            {desc.summary}
          </Text>
          <MetricExplainer label={primary.label}>
            <MetricExplainer.Formula>{desc.formula}</MetricExplainer.Formula>
            <MetricExplainer.Description>{desc.detail}</MetricExplainer.Description>
          </MetricExplainer>
        </div>
        <span className={`${TONE_TEXT[primary.tone]} inline-flex shrink-0 items-center gap-1 self-end`}>
          <Arrow className="size-3" strokeWidth={2.5} />
          <Text as="span" variant="body" tone="inherit">
            {primary.better === "higher" ? "higher is better" : "lower is better"}
          </Text>
        </span>
      </div>
    </div>
  )
}
