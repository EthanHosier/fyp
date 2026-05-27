import { parsePatchFiles } from "@pierre/diffs"
import { AlertTriangleIcon } from "lucide-react"
import type { ReactNode } from "react"

import type { Annotation } from "@/components/annotation-item"
import { AnnotationList } from "@/components/annotation-list"
import { MetricTile } from "@/components/metric-tile"
import { CleanlinessBreakdownContent } from "@/components/cleanliness-breakdown"
import { ProcessScoreBreakdownContent } from "@/components/process-score-breakdown"
import { StatusRow } from "@/components/status-row"
import { Text } from "@/components/text"
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from "@/components/ui/hover-card"
import type {
  CheckpointVM,
  DashboardViewModel,
  MetricId,
} from "@/data/types"
import { CodeSmellsSection } from "@/features/detail-panel/code-smells-section"
import {
  CommitSignals,
  hasCommitSignals,
} from "@/features/detail-panel/commit-signals"
import { DuplicationsSection } from "@/features/detail-panel/duplications-section"
import { DiffSection } from "@/features/detail-panel/diff-section"
import { DivergenceIndicators } from "@/features/detail-panel/divergence-indicators"
import { SmellSignals } from "@/features/detail-panel/smell-signals"

/**
 * Unified body for both checkpoint and refactoring selections. A
 * refactoring is rendered as the metrics + status of its `to` checkpoint,
 * plus the hunk-filtered patch instead of the raw transition diff —
 * passed in via the optional props.
 */
export function CheckpointBody({
  vm,
  checkpoint,
  pitfalls,
  patch,
  patchCacheKey,
  patchEmptyMessage = "No diff captured for this checkpoint.",
}: {
  vm: DashboardViewModel
  checkpoint: CheckpointVM
  pitfalls?: ReactNode
  patch?: string
  patchCacheKey?: string
  patchEmptyMessage?: string
}) {
  const baselines = metricBaselines(vm)
  const annotations = annotationsAt(vm, checkpoint.index)
  const effectivePatch = patch ?? checkpoint.patch
  const effectiveKey = patchCacheKey ?? `checkpoint-${checkpoint.sha}`
  const touchedFiles = effectivePatch
    ? (parsePatchFiles(effectivePatch, effectiveKey)[0]?.files ?? []).map(
        (f) => f.name,
      )
    : []

  return (
    <div className="flex flex-col gap-4">
      <section className="flex flex-col gap-2">
        <div className="flex items-center justify-between gap-2">
          <Text as="h3" variant="eyebrow" tone="fg-4">
            Metrics at {checkpoint.label}
          </Text>
          <CarryForwardBadge checkpoint={checkpoint} />
        </div>
        <div className="grid grid-cols-2 gap-2">
          {vm.metrics.map((m) => {
            const v = checkpoint.values[m.id]
            if (typeof v !== "number") return null
            const baseline = baselines[m.id]
            const delta =
              typeof baseline === "number" ? v - baseline : undefined
            return (
              <MetricTile
                key={m.id}
                label={m.label}
                value={v}
                unit={m.unit}
                delta={delta}
                better={m.better}
                hoverContent={tileHoverContent(m.id, checkpoint)}
              />
            )
          })}
        </div>
      </section>

      <section className="flex flex-col gap-2">
        <Text as="h3" variant="eyebrow" tone="fg-4">
          Status
        </Text>
        <StatusRow build={checkpoint.build} tests={checkpoint.tests} />
      </section>

      <DivergenceIndicators vm={vm} checkpointIndex={checkpoint.index} />

      {pitfalls ||
      hasSmellSignals(checkpoint) ||
      hasCommitSignals(vm, checkpoint.index) ? (
        <section className="flex flex-col gap-2">
          <Text as="h3" variant="eyebrow" tone="fg-4">
            Process Signals
          </Text>
          {pitfalls}
          <SmellSignals smells={checkpoint.smells} />
          <CommitSignals vm={vm} checkpoint={checkpoint} />
        </section>
      ) : null}

      {annotations.length > 0 ? (
        <AnnotationList annotations={annotations} />
      ) : null}

      <DiffSection
        title="Diff"
        patch={effectivePatch}
        cacheKey={effectiveKey}
        emptyMessage={patchEmptyMessage}
      />

      <DuplicationsSection
        duplications={checkpoint.duplications}
        checkpointSha={checkpoint.sha}
      />

      <CodeSmellsSection
        smells={checkpoint.smells}
        checkpointSha={checkpoint.sha}
        touchedFiles={touchedFiles}
      />
    </div>
  )
}

function hasSmellSignals(checkpoint: CheckpointVM): boolean {
  return checkpoint.smells.added.length > 0 || checkpoint.smells.resolved.length > 0
}

/** Inline pill rendered next to the "Metrics at <label>" header when
 *  this checkpoint's static cleanliness aggregates were carried
 *  forward (build or tests broken). Hovering shows the explanation;
 *  the tile values themselves stay clean. Renders nothing when the
 *  checkpoint is trustworthy. */
function CarryForwardBadge({ checkpoint }: { checkpoint: CheckpointVM }) {
  if (checkpoint.metricsTrustworthy) return null
  const isMidpoint = checkpoint.metricsCarryForwardSource === "MIDPOINT"
  return (
    <HoverCard openDelay={120} closeDelay={80}>
      <HoverCardTrigger asChild>
        <button
          type="button"
          aria-label="Carried forward from last green checkpoint"
          className="text-warn hover:text-warn/80 inline-flex cursor-help items-center gap-1"
        >
          <AlertTriangleIcon className="size-3" />
          <Text as="span" variant="caption" tone="inherit" className="text-[10px]">
            carried forward
          </Text>
        </button>
      </HoverCardTrigger>
      <HoverCardContent className="w-72">
        <div className="flex flex-col gap-1.5">
          <Text variant="eyebrow" tone="fg-4">
            Carried forward
          </Text>
          <Text variant="body" tone="fg-2" className="text-[12px] leading-relaxed">
            {isMidpoint
              ? "Build or tests were broken from the start of this session — cleanliness signals use the session midpoint as a placeholder."
              : "Build or tests were broken at this checkpoint, so static cleanliness signals can't be trusted. The values shown are carried forward from the last checkpoint where the build and tests both passed."}
          </Text>
        </div>
      </HoverCardContent>
    </HoverCard>
  )
}

/** Per-metric hover-card content. Process and cleanliness expose their
 *  decompositions; the six raw code metrics have no breakdown to show. */
function tileHoverContent(
  id: MetricId,
  checkpoint: CheckpointVM,
): ReactNode {
  if (id === "process") {
    return (
      <ProcessScoreBreakdownContent
        breakdown={checkpoint.processBreakdown}
      />
    )
  }
  if (id === "cleanliness" && checkpoint.cleanlinessBreakdown) {
    return (
      <CleanlinessBreakdownContent
        breakdown={checkpoint.cleanlinessBreakdown}
      />
    )
  }
  return undefined
}

/**
 * Synthesise per-checkpoint annotations from the existing VM. We don't
 * carry an `Annotation[]` on the server side; the chart's "annotation
 * lane" is purely a presentation concept derived from refactoring
 * steps and build/test status. Order is fixed: refactorings first,
 * then events — matches the reference's visual hierarchy.
 */
function annotationsAt(vm: DashboardViewModel, checkpointIndex: number): Annotation[] {
  const out: Annotation[] = []
  const cp = vm.checkpoints[checkpointIndex]

  for (const step of vm.refactoringSteps) {
    if (step.checkpointIndex !== checkpointIndex) continue
    out.push({
      id: `refactor-${step.index}`,
      kind: step.wasPerformedByIde ? "ide-refactor" : "detected-refactor",
      title: `${step.wasPerformedByIde ? "IDE" : "Detected"}: ${step.refactoringType}`,
      detail: step.description,
    })
  }

  // Build status transitions — only the *change* gets an annotation
  // so a long pass-streak or fail-streak doesn't repeat the event.
  const prev = vm.checkpoints[checkpointIndex - 1]
  if (cp?.build === "fail" && prev?.build === "pass") {
    out.push({
      id: `build-fail-${checkpointIndex}`,
      kind: "event",
      title: "Build broke",
      detail: `Passing at previous checkpoint → failing here, ${cp.churn} LOC changed`,
    })
  }
  if (cp?.build === "pass" && prev?.build === "fail") {
    out.push({
      id: `build-fixed-${checkpointIndex}`,
      kind: "resolved",
      title: "Build fixed",
      detail: `Failing at previous checkpoint → builds now pass, ${cp.churn} LOC changed`,
    })
  }
  if (cp?.tests === "fail" && prev?.tests === "pass") {
    out.push({
      id: `tests-fail-${checkpointIndex}`,
      kind: "event",
      title: "Tests broke",
      detail: `Passing at previous checkpoint → failing here, ${cp.churn} LOC changed`,
    })
  }
  if (cp?.tests === "pass" && prev?.tests === "fail") {
    out.push({
      id: `tests-fixed-${checkpointIndex}`,
      kind: "resolved",
      title: "Tests fixed",
      detail: `Failing at previous checkpoint → tests now pass, ${cp.churn} LOC changed`,
    })
  }

  // Metric spikes / drops: a single-checkpoint regression in the
  // "worse" direction whose magnitude exceeds [SPIKE_FRACTION] of the
  // metric's full observed range. Threshold is range-relative so the
  // detector self-tunes per metric — a 5-WMC complexity bump on a
  // project that ranges 0..50 is noisy, on one that ranges 0..200 is
  // not. Skipping i=0 (no prior to compare against).
  if (cp && prev) {
    for (const m of vm.metrics) {
      // Cleanliness drops are already conveyed by the process-score
      // breakdown panel; surfacing them again as a sidebar annotation
      // is redundant noise.
      if (m.id === "cleanliness") continue
      const a = prev.values[m.id]
      const b = cp.values[m.id]
      if (typeof a !== "number" || typeof b !== "number") continue
      const delta = b - a
      const regressed =
        m.better === "lower" ? delta > 0 : delta < 0
      if (!regressed) continue

      const range = metricRange(vm, m.id)
      if (range <= 0) continue
      const magnitude = Math.abs(delta) / range
      if (magnitude < SPIKE_FRACTION) continue

      const direction = m.better === "lower" ? "spike" : "drop"
      const dp = m.unit === "%" ? 1 : 0
      out.push({
        id: `spike-${m.id}-${checkpointIndex}`,
        kind: "event",
        title: `${m.label} ${direction}`,
        detail: `${delta > 0 ? "+" : "−"}${Math.abs(delta).toFixed(dp)} ${m.unit ?? ""} since previous checkpoint`,
      })
    }
  }

  return out
}

/**
 * Trip threshold for the spike/drop detector — fraction of a metric's
 * full observed range. 0.20 was picked by eyeballing real reports:
 * lower than ~0.15 produces noise on small metric movements, higher
 * than ~0.30 misses regressions worth surfacing.
 */
const SPIKE_FRACTION = 0.2

function metricRange(vm: DashboardViewModel, id: MetricId): number {
  let min = Infinity
  let max = -Infinity
  for (const c of vm.checkpoints) {
    const v = c.values[id]
    if (typeof v !== "number") continue
    if (v < min) min = v
    if (v > max) max = v
  }
  return Number.isFinite(min) && Number.isFinite(max) ? max - min : 0
}

/**
 * The baseline a metric tile compares against — first numeric value
 * across the trajectory. Mirrors the reference's `vals[0]`.
 */
function metricBaselines(vm: DashboardViewModel): Partial<Record<MetricId, number>> {
  const out: Partial<Record<MetricId, number>> = {}
  for (const m of vm.metrics) {
    const first = vm.checkpoints.find(
      (c) => typeof c.values[m.id] === "number",
    )
    const v = first?.values[m.id]
    if (typeof v === "number") out[m.id] = v
  }
  return out
}
