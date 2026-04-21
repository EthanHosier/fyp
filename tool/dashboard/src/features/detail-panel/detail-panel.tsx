import { X } from "lucide-react"

import { StatusDot } from "@/components/status-dot"
import { Text } from "@/components/text"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import type { CheckpointVM, DashboardViewModel, StatusTone } from "@/data/types"
import { CheckpointBody } from "@/features/detail-panel/checkpoint-body"
import { IntervalBody } from "@/features/detail-panel/interval-body"
import { StatusIntervalBody } from "@/features/detail-panel/status-interval-body"
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
    title = c.description
    subtitle = <CheckpointSubtitle checkpoint={c} />
    body = <CheckpointBody vm={vm} checkpoint={c} />
  } else if (selection.kind === "refactoring") {
    const step = vm.refactoringSteps[selection.index]
    if (!step) return null
    const to = vm.checkpoints[step.checkpointIndex]
    if (!to) return null
    title = step.refactoringType
    // Refactoring shares the checkpoint's time + build/tests subtitle —
    // the `to` checkpoint is the refactoring's endpoint state.
    subtitle = <CheckpointSubtitle checkpoint={to} tLabel={step.tLabel} />
    body = (
      <CheckpointBody
        vm={vm}
        checkpoint={to}
        description={step.description}
        patch={step.patch}
        patchCacheKey={`refactoring-${step.index}`}
        patchEmptyMessage="No filtered diff for this refactoring."
      />
    )
  } else if (selection.kind === "status") {
    const iv = vm.intervals[selection.intervalIndex]
    if (!iv) return null
    const status = selection.statusKind === "build" ? iv.build : iv.tests
    const statusLabel = statusLabelFor(selection.statusKind, status)
    const kindLabelText = selection.statusKind === "build" ? "Build" : "Tests"
    const runDurationMs = runDuration(vm, selection.intervalIndex, selection.statusKind, status)
    title = `${kindLabelText} ${statusLabel}`
    subtitle = (
      <span className="inline-flex items-center gap-1.5">
        <StatusDot tone={statusDotTone(status)} />
        <Text variant="mono" tone="fg-3">
          {statusLabel}
        </Text>
      </span>
    )
    body = (
      <StatusIntervalBody
        durationMs={runDurationMs}
        status={status}
        statusLabel={statusLabel}
      />
    )
  } else {
    const iv = vm.intervals[selection.index]
    if (!iv) return null
    title = `${endpointLabel(vm, iv.from)} → ${endpointLabel(vm, iv.to)}`
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

// Interval endpoints prefer the refactoring type landing on that
// checkpoint over the checkpoint's own `description`, because that's
// what the user sees as the chart's primary point there — otherwise
// adjacent detected refactorings both read as "Manual Edit".
function endpointLabel(vm: DashboardViewModel, checkpointIndex: number): string {
  const step = vm.refactoringSteps.find((s) => s.checkpointIndex === checkpointIndex)
  if (step) return step.refactoringType
  return vm.checkpoints[checkpointIndex]?.description ?? `c${checkpointIndex}`
}

// Tests being "unknown" specifically means the test run was skipped
// (build failed so we never ran them). Surface that distinction here —
// other statuses pass through unchanged.
function statusLabelFor(statusKind: "build" | "tests", status: StatusTone): string {
  if (statusKind === "tests" && status === "unknown") return "skipped"
  return status
}

// Rail rects merge adjacent same-status intervals into one visual run;
// the click dispatches the run's first interval index. Reconstruct the
// run by walking forward while the status matches, summing durations.
function runDuration(
  vm: DashboardViewModel,
  startIndex: number,
  statusKind: "build" | "tests",
  status: StatusTone,
): number {
  let total = 0
  for (let i = startIndex; i < vm.intervals.length; i++) {
    const iv = vm.intervals[i]
    const s = statusKind === "build" ? iv.build : iv.tests
    if (s !== status) break
    total += iv.durationMs
  }
  return total
}

function statusDotTone(s: StatusTone): "success" | "danger" | "neutral" {
  if (s === "pass") return "success"
  if (s === "fail") return "danger"
  return "neutral"
}

function CheckpointSubtitle({
  checkpoint,
  tLabel,
}: {
  checkpoint: CheckpointVM
  tLabel?: string
}) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <Text variant="mono" tone="fg-3">
        {tLabel ?? checkpoint.tLabel}
      </Text>
      <Text variant="mono" tone="fg-4">
        ·
      </Text>
      <StatusDot tone={statusDotTone(checkpoint.status)} />
      <Text variant="mono" tone="fg-3">
        {checkpoint.status}
      </Text>
    </span>
  )
}
