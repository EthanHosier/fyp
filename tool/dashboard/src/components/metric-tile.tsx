import { Text } from "@/components/text"
import { cn } from "@/lib/utils"

/**
 * Snapshot of a single metric at a point in time. Mirrors the
 * reference dashboard's tile: uppercase mono label, large mono value
 * with a small unit suffix, and an optional delta-from-baseline chip
 * coloured by whether the change is an improvement (per [better]).
 *
 * The progress-meter variant we used to render is gone — the
 * reference doesn't have one and the meter pulled the eye away from
 * the value. Range / fraction context lives on the chart itself.
 */
export function MetricTile({
  label,
  value,
  unit,
  delta,
  better = "lower",
  decimals,
}: {
  label: string
  value: number | string
  unit?: string
  /** Change from the baseline value. Omit (or pass 0) to hide the chip. */
  delta?: number
  better?: "lower" | "higher"
  /** Display precision for both value and delta. Defaults to 1 for `%`,
   *  0 otherwise — matches the reference. */
  decimals?: number
}) {
  const dp = decimals ?? (unit === "%" ? 1 : 0)
  const formattedValue =
    typeof value === "number" ? value.toFixed(dp) : value

  let deltaNode: React.ReactNode = null
  if (typeof delta === "number" && delta !== 0) {
    const improved = better === "lower" ? delta < 0 : delta > 0
    deltaNode = (
      <Text
        variant="mono"
        tone="inherit"
        className={cn(
          "text-[11px]",
          improved ? "text-good" : "text-bad",
        )}
      >
        {delta > 0 ? "+" : "−"}
        {Math.abs(delta).toFixed(dp)}
      </Text>
    )
  }

  return (
    <div className="border-border bg-bg-3 flex flex-col gap-0.5 rounded-sm border px-2.5 py-2">
      <Text
        as="div"
        variant="eyebrow"
        tone="fg-4"
        className="tracking-[0.06em]"
      >
        {label}
      </Text>
      <div className="flex items-baseline gap-2">
        <Text as="span" variant="monoStat" tone="fg">
          {formattedValue}
          {unit ? (
            <Text variant="caption" tone="fg-4" className="ml-0.5">
              {unit}
            </Text>
          ) : null}
        </Text>
        {deltaNode}
      </div>
    </div>
  )
}
