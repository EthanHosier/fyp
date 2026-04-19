import type { ReactNode } from "react"

/**
 * Side-by-side layout for design-system rows: a visual on the left, a token /
 * label caption on the right. Used by every token section.
 */
export function TokenRow({ visual, children }: { visual: ReactNode; children: ReactNode }) {
  return (
    <div className="flex items-center gap-3">
      {visual}
      {children}
    </div>
  )
}
