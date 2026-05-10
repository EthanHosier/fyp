import { DataList, DataListRow } from "@/components/data-list"
import { Text } from "@/components/text"
import type { CheckpointVM, DashboardViewModel } from "@/data/types"

/**
 * Per-metric `from → to (diff)` for one segment of an alt path. Mirrors
 * the regular IntervalBody's deltas section but works against any pair
 * of CheckpointVMs (real or alt-step synthetic) instead of an
 * IntervalVM keyed by checkpoint indexes.
 */
export function AltIntervalBody({
  vm,
  from,
  to,
}: {
  vm: DashboardViewModel
  from: CheckpointVM
  to: CheckpointVM
}) {
  return (
    <div className="flex flex-col gap-4">
      <section className="flex flex-col gap-2">
        <Text as="h3" variant="eyebrow" tone="fg-4">
          Deltas
        </Text>
        <DataList>
          {vm.metrics.map((m) => {
            const a = from.values[m.id]
            const b = to.values[m.id]
            if (typeof a !== "number" || typeof b !== "number") return null
            const diff = b - a
            const decimals = m.unit === "%" ? 1 : 0
            const tone =
              diff === 0
                ? "fg-3"
                : (m.better === "lower" ? diff < 0 : diff > 0)
                  ? "good"
                  : "bad"
            return (
              <DataListRow
                key={m.id}
                className="grid grid-cols-[1fr_auto_auto_auto] items-center gap-2.5"
              >
                <Text variant="body" tone="fg-2" className="text-[12px]">
                  {m.label}
                </Text>
                <Text variant="mono" tone="fg-4">
                  {a.toFixed(decimals)}
                </Text>
                <Text tone="fg-4">→</Text>
                <Text variant="mono" tone={tone} className="w-14 text-right">
                  {b.toFixed(decimals)}{" "}
                  <span className="opacity-65">
                    ({diff > 0 ? "+" : ""}
                    {diff.toFixed(decimals)})
                  </span>
                </Text>
              </DataListRow>
            )
          })}
        </DataList>
      </section>
    </div>
  )
}
