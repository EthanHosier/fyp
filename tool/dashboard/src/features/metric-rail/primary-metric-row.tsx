import { Sparkline } from "@/components/sparkline"
import { Text } from "@/components/text"
import type { MetricVM } from "@/data/types"
import { TONE_BORDER } from "@/lib/metric-tone"
import { cn } from "@/lib/utils"

/**
 * Radio-style row in the PRIMARY METRIC section. Active state pulls in
 * a left border + tinted background + sparkline in the metric's own
 * tone (complexity = mint, coupling = blue, …) so primary and overlay
 * both read the same colour for the same metric.
 */
export function PrimaryMetricRow({
  metric,
  values,
  active,
  onClick,
}: {
  metric: MetricVM
  values: number[]
  active: boolean
  onClick: () => void
}) {
  const delta = values.length >= 2 ? values[values.length - 1] - values[0] : 0
  const improved = metric.better === "lower" ? delta < 0 : delta > 0
  const decimals = metric.unit === "%" ? 1 : 0

  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex w-full cursor-pointer items-center gap-2 px-3 py-[7px] text-left transition-colors",
        "border-l-2 border-transparent",
        active ? cn("bg-bg-3", TONE_BORDER[metric.tone]) : "hover:bg-bg-2",
      )}
    >
      <div className="min-w-0 flex-1">
        <Text
          as="div"
          variant="body"
          tone={active ? "fg" : "fg-2"}
          className={cn("text-[12.5px]", active ? "font-semibold" : "font-medium")}
        >
          {metric.label}
        </Text>
        <Text as="div" variant="monoCaption" tone="fg-4">
          {metric.unit} · {metric.better === "lower" ? "lower = better" : "higher = better"}
        </Text>
      </div>
      <Sparkline values={values} tone={active ? metric.tone : "muted"} />
      <Text
        variant="monoCaption"
        tone={improved ? "good" : "bad"}
        className="inline-flex w-9 items-center justify-end gap-1"
      >
        <span>{improved ? "▼" : "▲"}</span>
        <span>{Math.abs(delta).toFixed(decimals)}</span>
      </Text>
    </button>
  )
}

