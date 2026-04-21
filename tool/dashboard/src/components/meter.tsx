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

// Gradient ramps into fully-saturated red at the 60% mark rather than
// the end, so meters hit the red zone a bit earlier — long bars look
// alarming sooner and short bars stay reassuringly green.
const meterFillStyles = cva("h-full", {
  variants: {
    better: {
      lower: "bg-[linear-gradient(to_right,var(--good),var(--bad)_60%)]",
      higher: "bg-[linear-gradient(to_left,var(--good),var(--bad)_60%)]",
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
      {/*
        The gradient is anchored to the full TRACK width — the fill is a
        window onto a track-wide rainbow, so a 20% value shows only the
        first 20% of the good→bad gradient (all green), a 100% value
        shows the whole thing. Inner div width = 1/frac so it scales up
        to track width inside the clipped fill.
      */}
      <div
        className="h-full overflow-hidden"
        style={{ width: `${frac * 100}%` }}
      >
        {frac > 0 ? (
          <div
            className={meterFillStyles({ better })}
            style={{ width: `${100 / frac}%` }}
          />
        ) : null}
      </div>
    </div>
  )
}
