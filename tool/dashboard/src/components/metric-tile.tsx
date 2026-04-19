import { Meter } from "@/components/meter"
import { Text } from "@/components/text"

/**
 * Snapshot of a single metric at a point in time: uppercase label,
 * monospace value + unit, rainbow Meter showing where the value sits
 * within its min/max range.
 */
export function MetricTile({
  label,
  value,
  unit,
  fraction,
  better = "lower",
}: {
  label: string
  value: number | string
  unit?: string
  /** 0..1 — where the value sits in its range (min=0, max=1). */
  fraction: number
  better?: "lower" | "higher"
}) {
  return (
    <div className="flex flex-col gap-1.5 rounded-sm border border-border bg-bg-2 px-2.5 py-2">
      <Text as="div" variant="eyebrow" tone="fg-4" className="tracking-[0.06em]">
        {label}
      </Text>
      <Text as="div" variant="monoStat" tone="fg">
        {value}
        {unit ? (
          <Text variant="caption" tone="fg-4" className="ml-1">
            {unit}
          </Text>
        ) : null}
      </Text>
      <Meter value={fraction} better={better} />
    </div>
  )
}
