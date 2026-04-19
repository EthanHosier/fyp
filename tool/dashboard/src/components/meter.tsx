import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

/**
 * Horizontal "rainbow" progress bar used to show how a metric's current
 * value sits within its min/max range. The fill is a good→bad (or
 * bad→good) gradient; `better` picks the direction.
 *
 * Port of the inline meter inside the reference dashboard's MetricTile.
 */

const meterTrackStyles = cva("w-full overflow-hidden rounded-[2px] bg-bg-3", {
  variants: {
    size: {
      sm: "h-[3px]",
      md: "h-[6px]",
    },
  },
  defaultVariants: { size: "sm" },
})

const meterFillStyles = cva("h-full", {
  variants: {
    better: {
      lower: "bg-gradient-to-r from-good to-bad",
      higher: "bg-gradient-to-r from-bad to-good",
    },
  },
  defaultVariants: { better: "lower" },
})

type MeterProps = VariantProps<typeof meterTrackStyles> &
  VariantProps<typeof meterFillStyles> & {
    /** 0..1 — clamped. */
    value: number
    className?: string
  }

export function Meter({ value, better, size, className }: MeterProps) {
  const frac = Math.max(0, Math.min(1, value))
  return (
    <div className={cn(meterTrackStyles({ size }), className)}>
      <div className={meterFillStyles({ better })} style={{ width: `${frac * 100}%` }} />
    </div>
  )
}
