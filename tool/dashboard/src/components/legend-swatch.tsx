import { cva, type VariantProps } from "class-variance-authority"

import { Text } from "@/components/text"
import { cn } from "@/lib/utils"

/**
 * Small icon + label pair used below the chart to identify each series.
 * The swatch itself is rendered as a child so callers can pick between
 * a solid line, a dashed stub, or a coloured square (for the interval
 * rail legend). Label is mono, fg-3, 11px.
 */
const legendSwatchStyles = cva("inline-flex items-center gap-1.5")

type LegendSwatchProps = VariantProps<typeof legendSwatchStyles> & {
  children: React.ReactNode
  label: string
  className?: string
}

export function LegendSwatch({ children, label, className }: LegendSwatchProps) {
  return (
    <span className={cn(legendSwatchStyles(), className)}>
      {children}
      <Text variant="mono" tone="fg-3" className="text-[11px]">
        {label}
      </Text>
    </span>
  )
}
