import type { ReactNode } from "react"

/**
 * Sidebar section wrapper: uppercase monospace title + body + bottom
 * divider. Used to group the metric rail's PRIMARY METRIC / OVERLAY /
 * LAYERS buckets.
 */
export function RailSection({
  title,
  children,
  description,
}: {
  title: string
  description?: ReactNode
  children: ReactNode
}) {
  return (
    <div className="border-b border-border py-3">
      <div className="font-mono text-[10.5px] tracking-[0.1em] text-fg-4 px-3 pb-2">
        {title}
      </div>
      {description ? (
        <div className="text-fg-4 px-3 pb-2 text-[11px]">{description}</div>
      ) : null}
      {children}
    </div>
  )
}
