import * as React from "react"

import { Text } from "@/components/text"
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from "@/components/ui/hover-card"

/**
 * "How it's calculated" hover-card for the chart's primary metric.
 *
 * Declarative — composed from dot-notation slots so callers don't
 * reach into the JSON shape of a descriptor:
 *
 *   <MetricExplainer label="Cognitive Complexity">
 *     <MetricExplainer.Formula>Σ (cognitive complexity per method)</MetricExplainer.Formula>
 *     <MetricExplainer.Description>A high score means …</MetricExplainer.Description>
 *   </MetricExplainer>
 */

function MetricExplainerRoot({
  label,
  triggerLabel = "How it's calculated",
  children,
}: {
  label: string
  triggerLabel?: string
  children: React.ReactNode
}) {
  return (
    <HoverCard openDelay={120} closeDelay={80}>
      <HoverCardTrigger asChild>
        <button
          type="button"
          className="text-fg-3 hover:text-fg cursor-help font-mono text-[11px] underline decoration-dotted underline-offset-4"
        >
          {triggerLabel}
        </button>
      </HoverCardTrigger>
      <HoverCardContent className="w-80" align="start">
        <Text as="div" variant="eyebrow" tone="fg-4">
          {label}
        </Text>
        <div className="mt-1.5 flex flex-col gap-2">{children}</div>
      </HoverCardContent>
    </HoverCard>
  )
}

function Formula({ children }: { children: React.ReactNode }) {
  return (
    <div className="border-border bg-bg-2 rounded-sm border px-2 py-1.5">
      <Text as="code" variant="mono" tone="fg">
        {children}
      </Text>
    </div>
  )
}

function Description({ children }: { children: React.ReactNode }) {
  return (
    <Text as="p" variant="bodySm" tone="fg-2">
      {children}
    </Text>
  )
}

export const MetricExplainer = Object.assign(MetricExplainerRoot, {
  Formula,
  Description,
})
