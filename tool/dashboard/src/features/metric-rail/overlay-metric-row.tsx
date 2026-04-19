import { Text } from "@/components/text"
import { Checkbox } from "@/components/ui/checkbox"
import type { MetricVM } from "@/data/types"
import { cn } from "@/lib/utils"

/**
 * Checkbox row in the OVERLAY (MAX 2) section. Always paints in the
 * metric's own tone — matches the primary row and the chart line so a
 * single colour identifies a metric everywhere it appears. Disabled
 * when inactive and both overlay slots are full.
 */
export function OverlayMetricRow({
  metric,
  active,
  disabled,
  onToggle,
}: {
  metric: MetricVM
  active: boolean
  disabled?: boolean
  onToggle: () => void
}) {
  const id = `overlay-${metric.id}`
  return (
    <label
      htmlFor={id}
      className={cn(
        "flex w-full items-center gap-2.5 px-3 py-[5px]",
        disabled ? "cursor-not-allowed" : "cursor-pointer hover:bg-bg-2",
        disabled && !active ? "opacity-50" : null,
      )}
    >
      <Checkbox
        id={id}
        tone={metric.tone}
        checked={active}
        disabled={disabled}
        onCheckedChange={onToggle}
      />
      <Text variant="body" tone={active ? "fg" : "fg-3"} className="flex-1 text-[12px]">
        {metric.label}
      </Text>
      <Text variant="monoCaption" tone="fg-4">
        {metric.unit}
      </Text>
    </label>
  )
}
