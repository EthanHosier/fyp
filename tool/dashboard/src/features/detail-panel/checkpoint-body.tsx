import { MetricTile } from "@/components/metric-tile"
import { StatusRow } from "@/components/status-row"
import { Text } from "@/components/text"
import type { CheckpointVM, DashboardViewModel, MetricId } from "@/data/types"
import { DiffSection } from "@/features/detail-panel/diff-section"

type Range = { min: number; max: number }

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
  patch,
  patchCacheKey,
  patchEmptyMessage = "No diff captured for this checkpoint.",
}: {
  vm: DashboardViewModel
  checkpoint: CheckpointVM
  description?: string
  patch?: string
  patchCacheKey?: string
  patchEmptyMessage?: string
}) {
  const ranges = metricRanges(vm)
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
            return (
              <MetricTile
                key={m.id}
                label={m.label}
                value={v}
                unit={m.unit}
                fraction={fractionFor(v, ranges[m.id])}
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

      <DiffSection
        title="Diff"
        patch={effectivePatch}
        cacheKey={effectiveKey}
        emptyMessage={patchEmptyMessage}
      />
    </div>
  )
}

function metricRanges(vm: DashboardViewModel): Partial<Record<MetricId, Range>> {
  const out: Partial<Record<MetricId, Range>> = {}
  for (const m of vm.metrics) {
    const values = vm.checkpoints
      .map((c) => c.values[m.id])
      .filter((v): v is number => typeof v === "number")
    if (values.length) {
      out[m.id] = { min: Math.min(...values), max: Math.max(...values) }
    }
  }
  return out
}

function fractionFor(value: number, range: Range | undefined): number {
  if (!range) return 0.5
  const span = range.max - range.min || 1
  return Math.max(0, Math.min(1, (value - range.min) / span))
}
