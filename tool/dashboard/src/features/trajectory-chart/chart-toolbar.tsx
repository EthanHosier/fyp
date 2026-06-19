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

  const bestAltTerminal =
    primary.id === "process" ? bestAltTerminalProcessScore(vm) : null
  const userTerminal =
    primary.id === "process"
      ? vm.checkpoints[vm.checkpoints.length - 1]?.processScore
      : undefined
  const showCounterfactual =
    bestAltTerminal != null &&
    typeof userTerminal === "number" &&
    bestAltTerminal > userTerminal

  const Arrow = primary.better === "higher" ? ArrowUpIcon : ArrowDownIcon

  return (
    <div className="px-1 pt-1 pb-[10px]">
      <Text as="div" variant="eyebrow" tone="fg-4">
        Trajectory · {primary.label}
      </Text>
      <Text as="div" variant="display" tone="fg" className="mt-0.5">
        {primary.label} over {vm.checkpoints.length} checkpoints:
        {showCounterfactual ? (
          <Text as="span" variant="display" tone="inherit" className="ml-1.5">
            Was {userTerminal!.toFixed(dp)} but{" "}
            <Text as="span" variant="display" tone="inherit" className="italic">
              could have
            </Text>{" "}
            been {" "}
            <Text as="span" variant="display" tone="brand">
              {bestAltTerminal!.toFixed(dp)}
            </Text>
          </Text>
        ) : first !== undefined && last !== undefined ? (
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

/** Highest terminal process score across alt trajectories, or null if
 *  none exist. Looks at each alt's final continuation step when present
 *  (alt merges back and we follow it to trace end), otherwise the alt's
 *  own last step. */
function bestAltTerminalProcessScore(vm: DashboardViewModel): number | null {
  let best: number | null = null
  for (const alt of vm.alternativeTrajectories) {
    const lastCont = alt.continuationSteps[alt.continuationSteps.length - 1]
    const lastStep = alt.steps[alt.steps.length - 1]
    const terminal = lastCont?.processScore ?? lastStep?.cpVm.processScore
    if (typeof terminal !== "number") continue
    if (best == null || terminal > best) best = terminal
  }
  return best
}
