import { Lightbulb } from "lucide-react"

import { RailSection } from "@/components/rail-section"
import { Text } from "@/components/text"
import type {
  CheckpointVM,
  DashboardViewModel,
  MetricVM,
  Selection,
} from "@/data/types"
import { TONE_BG } from "@/lib/metric-tone"
import { cn } from "@/lib/utils"
import { useDashboardStore } from "@/stores/dashboard-store"

const MAX_BULLETS = 4

type Delta = {
  metric: MetricVM
  from: number
  to: number
  diff: number
  improved: boolean
}

/**
 * Delta-bullet summary. Driven by the current selection:
 *   - no selection  → call-to-action prompt
 *   - checkpoint  → deltas vs. previous checkpoint (empty for c0)
 *   - interval    → deltas between `from` and `to`
 *
 * Prose and EXPLANATIONS-style copy are out of scope (PLAN).
 */
export function ExplanationCard({ vm }: { vm: DashboardViewModel }) {
  const selection = useDashboardStore((s) => s.selection)
  const deltas = deltasFor(vm, selection)

  return (
    <div className="overflow-auto">
      <RailSection
        title="What changed"
        icon={<Lightbulb className="size-3 text-brand" />}
        className="flex h-full flex-col"
      >
        <div className="px-3">
          {selection ? (
            deltas.length === 0 ? (
              <EmptyNote selection={selection} />
            ) : (
              <DeltaList deltas={deltas.slice(0, MAX_BULLETS)} />
            )
          ) : (
            <Prompt />
          )}
        </div>
      </RailSection>
    </div>
  )
}

function Prompt() {
  return (
    <Text as="p" variant="bodySm" tone="fg-3">
      Click a{" "}
      <Text as="strong" variant="bodySm" tone="fg-2" className="font-semibold">
        checkpoint
      </Text>
      , a{" "}
      <Text as="strong" variant="bodySm" tone="fg-2" className="font-semibold">
        build interval
      </Text>
      , or an{" "}
      <Text as="strong" variant="bodySm" tone="fg-2" className="font-semibold">
        annotation
      </Text>{" "}
      to see a narrated explanation here.
    </Text>
  )
}

function EmptyNote({ selection }: { selection: Selection }) {
  const label =
    selection?.kind === "checkpoint" && selection.index === 0
      ? "First checkpoint — nothing to compare against yet."
      : "No metric deltas available for this selection."
  return (
    <Text as="p" variant="bodySm" tone="fg-4">
      {label}
    </Text>
  )
}

function DeltaList({ deltas }: { deltas: Delta[] }) {
  return (
    <ul className="flex flex-col gap-1.5">
      {deltas.map((d) => (
        <li key={d.metric.id} className="flex items-center gap-2">
          <span
            className={cn("inline-block size-2 shrink-0 rounded-full", TONE_BG[d.metric.tone])}
          />
          <Text variant="body" tone="fg-2" className="text-[12px]">
            {d.metric.label}
          </Text>
          <Text variant="mono" tone="fg-4" className="ml-auto">
            {formatValue(d.from, d.metric)} →{" "}
            {formatValue(d.to, d.metric)}
          </Text>
          <Text
            variant="mono"
            tone={d.improved ? "good" : "bad"}
            className="inline-flex w-14 items-center justify-end gap-1"
          >
            <span>{d.improved ? "▼" : "▲"}</span>
            <span>{formatDiff(d.diff, d.metric)}</span>
          </Text>
        </li>
      ))}
    </ul>
  )
}

function deltasFor(vm: DashboardViewModel, selection: Selection): Delta[] {
  if (!selection) return []
  if (selection.kind === "checkpoint") {
    if (selection.index <= 0) return []
    return computeDeltas(
      vm,
      vm.checkpoints[selection.index - 1],
      vm.checkpoints[selection.index],
    )
  }
  if (selection.kind === "refactoring") {
    const step = vm.refactoringSteps[selection.index]
    if (!step) return []
    const to = vm.checkpoints[step.checkpointIndex]
    const from =
      vm.checkpoints.find((c) => c.sha === step.fromSha) ??
      vm.checkpoints[Math.max(0, step.checkpointIndex - 1)]
    if (!from || !to) return []
    return computeDeltas(vm, from, to)
  }
  const iv = vm.intervals[selection.index]
  if (!iv) return []
  return computeDeltas(vm, vm.checkpoints[iv.from], vm.checkpoints[iv.to])
}

function computeDeltas(
  vm: DashboardViewModel,
  from: CheckpointVM,
  to: CheckpointVM,
): Delta[] {
  const out: Delta[] = []
  for (const m of vm.metrics) {
    const a = from.values[m.id]
    const b = to.values[m.id]
    if (typeof a !== "number" || typeof b !== "number") continue
    const diff = b - a
    if (diff === 0) continue
    out.push({
      metric: m,
      from: a,
      to: b,
      diff,
      improved: m.better === "lower" ? diff < 0 : diff > 0,
    })
  }
  return out.sort(
    (a, b) =>
      Math.abs(relativeMagnitude(b)) - Math.abs(relativeMagnitude(a)),
  )
}

/** Rank by proportional change so small-range metrics still surface. */
function relativeMagnitude(d: Delta): number {
  const base = Math.max(Math.abs(d.from), Math.abs(d.to), 1)
  return d.diff / base
}

function formatValue(v: number, m: MetricVM): string {
  return v.toFixed(m.unit === "%" ? 1 : 0)
}

function formatDiff(diff: number, m: MetricVM): string {
  return Math.abs(diff).toFixed(m.unit === "%" ? 1 : 0)
}
