import { MetricTile } from "@/components/metric-tile"
import { StatusRow } from "@/components/status-row"
import { Text } from "@/components/text"
import type { CheckpointVM, DashboardViewModel, MetricId } from "@/data/types"
import { DiffSection } from "@/features/detail-panel/diff-section"

type Range = { min: number; max: number }

/**
 * Details for a single checkpoint — metric tiles (with rainbow meter
 * driven by where the value sits in its observed min/max range) plus
 * a build/tests StatusRow. No touched-files preview until we map
 * `diff.perFileChurn` into the view-model (out of scope per PLAN).
 */
export function CheckpointBody({
  vm,
  checkpoint,
}: {
  vm: DashboardViewModel
  checkpoint: CheckpointVM
}) {
  const ranges = metricRanges(vm)

  return (
    <div className="flex flex-col gap-4">
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
                fraction={fractionFor(v, ranges[m.id], m.better)}
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
        patch={checkpoint.patch}
        cacheKey={`checkpoint-${checkpoint.sha}`}
        emptyMessage="No diff captured for this checkpoint."
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

function fractionFor(
  value: number,
  range: Range | undefined,
  _better: "lower" | "higher",
): number {
  if (!range) return 0.5
  const span = range.max - range.min || 1
  return Math.max(0, Math.min(1, (value - range.min) / span))
}
