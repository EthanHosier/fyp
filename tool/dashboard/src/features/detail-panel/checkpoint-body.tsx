import type { ReactNode } from "react"

import { MetricTile } from "@/components/metric-tile"
import { StatusRow } from "@/components/status-row"
import { Text } from "@/components/text"
import type { CheckpointVM, DashboardViewModel, MetricId } from "@/data/types"
import { DiffSection } from "@/features/detail-panel/diff-section"

/**
 * Unified body for both checkpoint and refactoring selections. A
 * refactoring is rendered as the metrics + status of its `to` checkpoint,
 * plus the RM description at the top and the hunk-filtered patch instead
 * of the raw transition diff — passed in via the optional props.
 */
export function CheckpointBody({
  vm,
  checkpoint,
  description,
  pitfalls,
  patch,
  patchCacheKey,
  patchEmptyMessage = "No diff captured for this checkpoint.",
}: {
  vm: DashboardViewModel
  checkpoint: CheckpointVM
  description?: string
  pitfalls?: ReactNode
  patch?: string
  patchCacheKey?: string
  patchEmptyMessage?: string
}) {
  const baselines = metricBaselines(vm)
  const effectivePatch = patch ?? checkpoint.patch
  const effectiveKey = patchCacheKey ?? `checkpoint-${checkpoint.sha}`

  return (
    <div className="flex flex-col gap-4">
      {description ? (
        <section className="flex flex-col gap-2">
          <Text as="h3" variant="eyebrow" tone="fg-4">
            Description
          </Text>
          <Text variant="body" tone="fg-2" className="text-[12px]">
            {description}
          </Text>
        </section>
      ) : null}

      <section className="flex flex-col gap-2">
        <Text as="h3" variant="eyebrow" tone="fg-4">
          Metrics at {checkpoint.label}
        </Text>
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

      {pitfalls}

      <DiffSection
        title="Diff"
        patch={effectivePatch}
        cacheKey={effectiveKey}
        emptyMessage={patchEmptyMessage}
      />
    </div>
  )
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
