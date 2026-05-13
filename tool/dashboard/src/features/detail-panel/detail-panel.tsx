import { X } from "lucide-react"

import { StatusDot } from "@/components/status-dot"
import { Text } from "@/components/text"
import { Button } from "@/components/ui/button"
import type { CheckpointVM, DashboardViewModel, StatusTone } from "@/data/types"
import { AlternativeBody } from "@/features/detail-panel/alternative-body"
import { AltIntervalBody } from "@/features/detail-panel/alt-interval-body"
import { CheckpointBody } from "@/features/detail-panel/checkpoint-body"
import { DivergenceBody } from "@/features/detail-panel/divergence-body"
import { IntervalBody } from "@/features/detail-panel/interval-body"
import {
  hasRefactoringPitfalls,
  RefactoringPitfalls,
} from "@/features/detail-panel/refactoring-pitfalls"
import {
  StatusIntervalBody,
  statusLabelFor,
} from "@/features/detail-panel/status-interval-body"
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
        pitfalls={
          hasRefactoringPitfalls(step) ? (
            <RefactoringPitfalls vm={vm} step={step} />
          ) : undefined
        }
        patch={step.patch}
        patchCacheKey={`refactoring-${step.index}`}
        patchEmptyMessage="No filtered diff for this refactoring."
      />
    )
  } else if (selection.kind === "altCheckpoint") {
    const alt = vm.alternativeTrajectories.find((a) => a.index === selection.altIndex)
    const step = alt?.steps[selection.stepIndex]
    if (!alt || !step) return null
    title = step.label
    subtitle = (
      <span className="inline-flex items-center gap-1.5">
        <Text variant="mono" tone="fg-3">
          alt step {selection.stepIndex + 1}/{alt.steps.length}
        </Text>
        <Text variant="mono" tone="fg-4">
          ·
        </Text>
        <Text variant="mono" tone="fg-3">
          {step.shortAltSha}
        </Text>
      </span>
    )
    body = (
      <CheckpointBody
        vm={vm}
        checkpoint={step.cpVm}
        patch={step.patch}
        patchCacheKey={`alt-step-${alt.index}-${selection.stepIndex}`}
        patchEmptyMessage="No diff captured for this alt step."
      />
    )
  } else if (selection.kind === "altContinuation") {
    const alt = vm.alternativeTrajectories.find((a) => a.index === selection.altIndex)
    const step = alt?.continuationSteps[selection.continuationIndex]
    if (!alt || !step) return null
    const userVm = vm.checkpoints[step.checkpointIndex]
    title = userVm?.description ?? step.cpVm.label
    subtitle = (
      <span className="inline-flex items-center gap-1.5">
        <Text variant="mono" tone="fg-3">
          {step.cpVm.shortSha}
        </Text>
        <Text variant="mono" tone="fg-4">
          ·
        </Text>
        <Text variant="mono" tone="fg-3">
          alt continuation
        </Text>
        <Text variant="mono" tone="fg-4">
          ·
        </Text>
        <Text variant="mono" tone="fg-3">
          process {step.processScore} vs user {userVm?.processScore ?? "—"}
        </Text>
      </span>
    )
    body = (
      <CheckpointBody
        vm={vm}
        checkpoint={step.cpVm}
        patch={step.cpVm.patch}
        patchCacheKey={`alt-continuation-${alt.index}-${selection.continuationIndex}`}
        patchEmptyMessage="No diff captured for this checkpoint."
      />
    )
  } else if (selection.kind === "altInterval") {
    const alt = vm.alternativeTrajectories.find((a) => a.index === selection.altIndex)
    if (!alt) return null
    const fromCp = vm.checkpoints[alt.fromCheckpointIndex]
    const toCp = vm.checkpoints[alt.toCheckpointIndex]
    const segIdx = selection.segmentIndex
    const fromVm =
      segIdx === 0 ? fromCp : alt.steps[segIdx - 1]?.cpVm
    const toVm =
      segIdx === alt.steps.length ? toCp : alt.steps[segIdx]?.cpVm
    if (!fromVm || !toVm) return null
    title = `${fromVm.label} → ${toVm.label}`
    subtitle = (
      <span className="inline-flex items-center gap-1.5">
        <Text variant="mono" tone="fg-3">
          alt segment {segIdx + 1}/{alt.steps.length + 1}
        </Text>
      </span>
    )
    body = <AltIntervalBody vm={vm} from={fromVm} to={toVm} />
  } else if (selection.kind === "divergencePoint") {
    const dp = vm.divergencePoints.find((d) => d.id === selection.dpId)
    if (!dp) return null
    title = dp.title
    subtitle = (
      <span className="inline-flex items-center gap-1.5">
        <Text variant="mono" tone="fg-3">
          {dp.kind.toLowerCase().replace("_", " ")}
        </Text>
        <Text variant="mono" tone="fg-4">
          ·
        </Text>
        <Text variant="mono" tone="fg-3">
          step {dp.stepIndex}
        </Text>
      </span>
    )
    body = <DivergenceBody vm={vm} dp={dp} />
  } else if (selection.kind === "alternative") {
    const alt = vm.alternativeTrajectories.find((a) => a.index === selection.index)
    if (!alt) return null
    const fromCp = vm.checkpoints[alt.fromCheckpointIndex]
    const toCp = vm.checkpoints[alt.toCheckpointIndex]
    const span = fromCp && toCp ? `${fromCp.label} → ${toCp.label}` : alt.branchRef
    title = alt.label
    subtitle = (
      <span className="inline-flex items-center gap-1.5">
        <Text variant="mono" tone="fg-3">
          {span}
        </Text>
        <Text variant="mono" tone="fg-4">
          ·
        </Text>
        <Text variant="mono" tone="fg-3">
          {alt.shortAltSha}
        </Text>
      </span>
    )
    body = <AlternativeBody vm={vm} alt={alt} />
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
      {/* Native overflow rather than Radix ScrollArea: in IntelliJ's embedded
          JCEF browser the custom-thumb position calc on every scroll event
          stalls the main thread. Native scrolling is GPU-composited there. */}
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
        <div className="px-4 py-3">{body}</div>
      </div>
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
        {checkpoint.shortSha}
      </Text>
      <Text variant="mono" tone="fg-4">
        ·
      </Text>
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
