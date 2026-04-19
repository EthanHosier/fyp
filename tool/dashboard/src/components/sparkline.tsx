import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

/**
 * Tiny inline SVG sparkline showing a value series. Ported from the
 * reference's MetricRow mini-sparkline (60x18, 1px padding, linear
 * polyline). No curves — reference is straight-segmented.
 */

const sparklineStyles = cva("block", {
  variants: {
    tone: {
      brand: "text-brand",
      brand2: "text-brand-2",
      brand3: "text-brand-3",
      muted: "text-fg-4",
    },
  },
  defaultVariants: { tone: "muted" },
})

type SparklineProps = VariantProps<typeof sparklineStyles> & {
  values: number[]
  width?: number
  height?: number
  strokeWidth?: number
  className?: string
}

export function Sparkline({
  values,
  width = 60,
  height = 18,
  strokeWidth = 1.3,
  tone,
  className,
}: SparklineProps) {
  if (values.length < 2) return null
  const pad = 1
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1
  const d = values
    .map((v, i) => {
      const x = (i / (values.length - 1)) * (width - 2 * pad) + pad
      const y = height - pad - ((v - min) / range) * (height - 2 * pad)
      return `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(" ")

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      width={width}
      height={height}
      className={cn(sparklineStyles({ tone }), className)}
    >
      <path d={d} fill="none" stroke="currentColor" strokeWidth={strokeWidth} />
    </svg>
  )
}
