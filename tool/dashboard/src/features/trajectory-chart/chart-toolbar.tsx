import { FilterChip } from "@/components/filter-chip"
import { Text } from "@/components/text"
import type { DashboardViewModel, MetricVM } from "@/data/types"

/**
 * Header strip above the chart canvas. Mirrors the reference:
 * eyebrow "TRAJECTORY · {metric}", display title "{label} over N
 * checkpoints", and a mono caption showing the first → last value.
 * Right side: decorative filter chips (non-functional for now).
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
  const first = values[0]
  const last = values[values.length - 1]

  return (
    <div className="flex items-center gap-3 px-1 pt-1 pb-[10px]">
      <div>
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
      </div>
      <div className="flex-1" />
      <FilterChip active>x · checkpoint</FilterChip>
      <FilterChip>zoom · fit</FilterChip>
    </div>
  )
}
