import { HelpCircleIcon } from "lucide-react"
import type { ReactNode } from "react"

import { Text } from "@/components/text"
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from "@/components/ui/hover-card"
import { cn } from "@/lib/utils"

/** Snapshot of a single metric at a point in time. */
export function MetricTile({
  label,
  value,
  unit,
  delta,
  better = "lower",
  decimals,
  hoverContent,
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
  /** When set, a "?" icon appears next to the label and reveals a
   *  hover-card with this content — used by the Process Score tile to
   *  surface its breakdown without taking up panel space. */
  hoverContent?: ReactNode
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
      <div className="flex items-baseline justify-between gap-2">
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
        {hoverContent ? (
          <HoverCard openDelay={120} closeDelay={80}>
            <HoverCardTrigger asChild>
              <button
                type="button"
                aria-label={`${label} breakdown`}
                className="text-fg-4 hover:text-fg-2 -mr-0.5 inline-flex translate-y-[1px] cursor-help items-center self-center"
              >
                <HelpCircleIcon className="size-3.5" />
              </button>
            </HoverCardTrigger>
            <HoverCardContent className="w-72">{hoverContent}</HoverCardContent>
          </HoverCard>
        ) : null}
      </div>
    </div>
  )
}
