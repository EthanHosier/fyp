import { Text } from "@/components/text"
import type { ProcessScoreBreakdown } from "@/data/types"
import { cn } from "@/lib/utils"

/**
 * Decomposed view of a process score: the baseline anchor plus one row
 * per signed contribution (cleanliness gain, broken checkpoints, smells,
 * tests skipped, manual-when-IDE). Rendered inside a hover-card on the
 * Process Score metric tile so the explainer doesn't take detail-panel
 * real-estate when the user isn't asking for it.
 */
export function ProcessScoreBreakdownContent({
  breakdown,
}: {
  breakdown: ProcessScoreBreakdown
}) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-baseline justify-between gap-3">
        <Text as="div" variant="eyebrow" tone="fg-4">
          Process score
        </Text>
        <Text variant="monoStat" tone="fg" className="text-[15px]">
          {Math.round(breakdown.total)}
          <Text variant="mono" tone="fg-4" className="font-normal">
            /100
          </Text>
        </Text>
      </div>
      <div className="flex flex-col gap-1">
        <ContributionRow
          label="Baseline"
          points={breakdown.baseline}
          detail="anchor"
          isBaseline
        />
        {breakdown.contributions.map((c) => (
          <ContributionRow
            key={c.id}
            label={c.label}
            points={c.points}
            detail={c.detail}
          />
        ))}
      </div>
      {breakdown.clamped ? (
        <Text variant="caption" tone="fg-4" className="italic">
          Clamped to 0–100 — raw contributions sum outside that range.
        </Text>
      ) : null}
    </div>
  )
}

function ContributionRow({
  label,
  points,
  detail,
  isBaseline = false,
}: {
  label: string
  points: number
  detail: string
  isBaseline?: boolean
}) {
  // Round signed points to 1 dp — sub-1-point contributions otherwise read
  // as "0" and look like noise even though they nudged the score.
  const rounded = Math.round(points * 10) / 10
  const sign = rounded > 0 ? "+" : rounded < 0 ? "−" : ""
  const tone = isBaseline
    ? "fg-3"
    : rounded > 0
      ? "good"
      : rounded < 0
        ? "bad"
        : "fg-4"
  return (
    <div className="flex items-baseline justify-between gap-3 text-[12px]">
      <div className="flex min-w-0 flex-col">
        <Text variant="bodySm" tone="fg-2" className="truncate">
          {label}
        </Text>
        <Text variant="caption" tone="fg-4" className="truncate">
          {detail}
        </Text>
      </div>
      <Text
        variant="mono"
        tone={tone}
        className={cn(
          "shrink-0 tabular-nums",
          isBaseline ? "" : "font-semibold",
        )}
      >
        {isBaseline ? rounded : `${sign}${Math.abs(rounded)}`}
      </Text>
    </div>
  )
}
