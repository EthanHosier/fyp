import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

/**
 * Header "Process score" pill: muted label + monospace value/total + delta
 * arrow. Delta sign drives the arrow colour via the `delta` value; pass 0
 * for a flat read-out.
 */

const scorePillStyles = cva(
  "inline-flex items-center gap-2.5 rounded-md border border-border-strong bg-bg-2 text-fg",
  {
    variants: {
      size: {
        sm: "h-6 px-2 text-[11px]",
        md: "h-7 px-2.5 text-xs",
      },
    },
    defaultVariants: { size: "md" },
  },
)

type ScorePillProps = VariantProps<typeof scorePillStyles> & {
  label: string
  value: number
  total?: number
  delta?: number
  className?: string
}

export function ScorePill({ label, value, total = 100, delta, size, className }: ScorePillProps) {
  const showDelta = typeof delta === "number"
  const up = (delta ?? 0) >= 0
  return (
    <div className={cn(scorePillStyles({ size }), className)}>
      <span className="text-fg-3 text-[11px]">{label}</span>
      <span className="font-mono text-sm font-semibold text-fg">
        {value}
        <span className="text-fg-4 font-normal">/{total}</span>
      </span>
      {showDelta ? (
        <span
          className={cn(
            "inline-flex items-center gap-1 font-mono text-[11px]",
            up ? "text-good" : "text-bad",
          )}
        >
          <span>{up ? "▲" : "▼"}</span>
          <span>{Math.abs(delta as number)}</span>
        </span>
      ) : null}
    </div>
  )
}
