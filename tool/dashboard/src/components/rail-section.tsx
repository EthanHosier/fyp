import type { ReactNode } from "react"

import { Text } from "@/components/text"
import { cn } from "@/lib/utils"

/**
 * Sidebar section wrapper: uppercase monospace title + body + bottom
 * divider. Used to group the metric rail's PRIMARY METRIC / OVERLAY /
 * LAYERS buckets.
 */
export function RailSection({
  title,
  icon,
  children,
  description,
  className,
}: {
  title: string
  icon?: ReactNode
  description?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <div className={cn("border-b border-border py-3", className)}>
      <Text
        as="div"
        variant="eyebrow"
        tone="fg-4"
        className="flex items-center gap-1.5 px-3 pb-2"
      >
        {icon}
        <span>{title}</span>
      </Text>
      {description ? (
        <Text as="div" variant="caption" tone="fg-4" className="px-3 pb-2">
          {description}
        </Text>
      ) : null}
      {children}
    </div>
  )
}
