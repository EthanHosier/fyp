import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

/**
 * Small monospace chip used in the chart toolbar. Two states — `active`
 * pulls a slightly deeper background + brighter border, mirroring the
 * reference's "x · checkpoint" / "zoom · fit" / "export" chips.
 */
const filterChipStyles = cva(
  "inline-flex h-7 shrink-0 cursor-pointer items-center rounded-sm border px-2.5 font-mono text-[11px] transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
  {
    variants: {
      active: {
        true: "bg-bg-3 border-border-strong text-fg",
        false: "bg-bg-2 border-border text-fg-3 hover:text-fg-2",
      },
    },
    defaultVariants: { active: false },
  },
)

type FilterChipProps = React.ButtonHTMLAttributes<HTMLButtonElement> &
  VariantProps<typeof filterChipStyles>

export function FilterChip({ className, active, ...rest }: FilterChipProps) {
  return (
    <button
      type="button"
      aria-pressed={active ?? undefined}
      className={cn(filterChipStyles({ active }), className)}
      {...rest}
    />
  )
}
