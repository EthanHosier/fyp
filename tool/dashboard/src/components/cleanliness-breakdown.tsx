import { Text } from "@/components/text"
import type { CleanlinessBreakdown } from "@/data/types"
import { cn } from "@/lib/utils"

/**
 * Decomposed view of the cleanliness composite: each contributing
 * sub-metric with its literature-informed weight, normalised score
 * (0..1, where 1 = best observed in the trajectory), and the points it
 * adds to the displayed 0..100 total. Mirrors the process-score
 * breakdown shape so both hover-cards feel consistent.
 */
export function CleanlinessBreakdownContent({
  breakdown,
}: {
  breakdown: CleanlinessBreakdown
}) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-baseline justify-between gap-3">
        <Text as="div" variant="eyebrow" tone="fg-4">
          Code cleanliness
        </Text>
        <Text variant="monoStat" tone="fg" className="text-[15px]">
          {breakdown.total}
          <Text variant="mono" tone="fg-4" className="font-normal">
            /100
          </Text>
        </Text>
      </div>
      <div className="flex flex-col gap-1">
        {breakdown.contributions.map((c) => (
          <SubMetricRow
            key={c.id}
            label={c.label}
            weight={c.weight}
            normalised={c.normalised}
            points={c.points}
          />
        ))}
      </div>
      {breakdown.rebased ? (
        <Text variant="caption" tone="fg-4" className="italic">
          Some metrics unavailable; weights re-based across the rest.
        </Text>
      ) : null}
    </div>
  )
}

function SubMetricRow({
  label,
  weight,
  normalised,
  points,
}: {
  label: string
  weight: number
  normalised: number
  points: number
}) {
  // Show: label + weight share on the left, "raw normalised" + points
  // contribution on the right. Points are rounded to 1dp — sub-1pt
  // contributions still nudge the total visibly so they shouldn't
  // disappear into 0.
  const pts = Math.round(points * 10) / 10
  return (
    <div className="flex items-baseline justify-between gap-3 text-[12px]">
      <div className="flex min-w-0 flex-col">
        <Text variant="bodySm" tone="fg-2" className="truncate">
          {label}
        </Text>
        <Text variant="caption" tone="fg-4" className="truncate">
          weight {weight.toFixed(2)} · normalised {normalised.toFixed(2)}
        </Text>
      </div>
      <Text
        variant="mono"
        tone="fg"
        className={cn("shrink-0 font-semibold tabular-nums")}
      >
        {pts.toFixed(1)}
      </Text>
    </div>
  )
}
