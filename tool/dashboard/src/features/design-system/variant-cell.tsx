import type { ReactNode } from "react"

/**
 * Labels a rendered component variant with a small monospace caption.
 * Used inside a showcase-layout DesignSystemSection to put the label next to
 * each example instead of above/below.
 */
export function VariantCell({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col items-start gap-2">
      <code className="text-fg-3 font-mono text-[11px]">{label}</code>
      {children}
    </div>
  )
}
