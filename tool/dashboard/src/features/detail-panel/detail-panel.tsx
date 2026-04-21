import { X } from "lucide-react"

import { StatusDot } from "@/components/status-dot"
import { Text } from "@/components/text"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import type { DashboardViewModel, StatusTone } from "@/data/types"
import { CheckpointBody } from "@/features/detail-panel/checkpoint-body"
import { IntervalBody } from "@/features/detail-panel/interval-body"
import { RefactoringBody } from "@/features/detail-panel/refactoring-body"
import { useDashboardStore } from "@/stores/dashboard-store"

const PANEL_WIDTH = 380

/**
 * Right-side slide-in panel showing details for the current selection.
 * Returns null when nothing is selected — entry animation uses
 * tw-animate-css utilities, no exit animation (close is instant).
 */
export function DetailPanel({ vm }: { vm: DashboardViewModel }) {
  const selection = useDashboardStore((s) => s.selection)
  const setSelection = useDashboardStore((s) => s.setSelection)

  if (!selection) return null

  const kindLabel = selection.kind
  let title: string
  let subtitle: React.ReactNode
  let body: React.ReactNode

  if (selection.kind === "checkpoint") {
    const c = vm.checkpoints[selection.index]
    if (!c) return null
    title = c.label
    subtitle = (
      <span className="inline-flex items-center gap-1.5">
        <Text variant="mono" tone="fg-3">
          {c.tLabel}
        </Text>
        <Text variant="mono" tone="fg-4">
          ·
        </Text>
        <StatusDot tone={statusDotTone(c.status)} />
        <Text variant="mono" tone="fg-3">
          {c.status}
        </Text>
      </span>
    )
    body = <CheckpointBody vm={vm} checkpoint={c} />
  } else if (selection.kind === "refactoring") {
    const step = vm.refactoringSteps[selection.index]
    if (!step) return null
    title = step.refactoringType
    subtitle = (
      <span className="inline-flex items-center gap-1.5">
        <Text variant="mono" tone="fg-3">
          r{step.index}
        </Text>
        <Text variant="mono" tone="fg-4">
          ·
        </Text>
        <Text variant="mono" tone="fg-3">
          {step.tLabel}
        </Text>
      </span>
    )
    body = <RefactoringBody vm={vm} step={step} />
  } else {
    const iv = vm.intervals[selection.index]
    if (!iv) return null
    title = `Interval c${iv.from} → c${iv.to}`
    subtitle = (
      <span className="inline-flex items-center gap-1.5">
        <StatusDot tone={statusDotTone(iv.status)} />
        <Text variant="mono" tone="fg-3">
          {iv.status}
        </Text>
      </span>
    )
    body = <IntervalBody vm={vm} interval={iv} />
  }

  return (
    <aside
      style={{ width: PANEL_WIDTH }}
      className="border-border bg-bg-1 flex shrink-0 flex-col border-l animate-in slide-in-from-right duration-200"
    >
      <header className="border-border flex items-start gap-2.5 border-b px-4 py-3.5">
        <div className="flex min-w-0 flex-1 flex-col gap-0.5">
          <Text variant="eyebrow" tone="fg-4">
            {kindLabel}
          </Text>
          <Text as="h2" variant="heading" tone="fg">
            {title}
          </Text>
          <div>{subtitle}</div>
        </div>
        <Button
          variant="outline"
          size="icon"
          aria-label="Close details"
          onClick={() => setSelection(null)}
        >
          <X />
        </Button>
      </header>
      <ScrollArea className="min-h-0 flex-1">
        <div className="px-4 py-3">{body}</div>
      </ScrollArea>
    </aside>
  )
}

function statusDotTone(s: StatusTone): "success" | "danger" | "neutral" {
  if (s === "pass") return "success"
  if (s === "fail") return "danger"
  return "neutral"
}
