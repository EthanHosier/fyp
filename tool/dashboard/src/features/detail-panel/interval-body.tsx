import { DataList, DataListRow } from "@/components/data-list"
import { StatusDot } from "@/components/status-dot"
import { Text } from "@/components/text"
import type {
  DashboardViewModel,
  IntervalVM,
  StatusTone,
} from "@/data/types"
import { formatDurationShort } from "@/lib/format"

/**
 * Details for an interval between two consecutive checkpoints — one
 * row per metric showing from → to (diff), plus timing and churn
 * snippets pulled from the view-model.
 */
export function IntervalBody({
  vm,
  interval,
}: {
  vm: DashboardViewModel
  interval: IntervalVM
}) {
  const from = vm.checkpoints[interval.from]
  const to = vm.checkpoints[interval.to]

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

      <section className="flex flex-col gap-2">
        <Text as="h3" variant="eyebrow" tone="fg-4">
          Timing
        </Text>
        <KeyValue k="Duration" v={formatDurationShort(interval.durationMs)} />
        <KeyValue
          k="Status"
          v={
            <span className="inline-flex items-center gap-1.5">
              <StatusDot tone={statusDotTone(interval.status)} />
              <Text variant="mono" tone="fg-2">
                {interval.status}
              </Text>
            </span>
          }
        />
        <KeyValue k="Churn" v={`${interval.churn} LOC`} />
      </section>
    </div>
  )
}

function KeyValue({ k, v }: { k: string; v: React.ReactNode }) {
  return (
    <div className="grid grid-cols-[100px_1fr] items-center gap-2.5 py-[3px]">
      <Text variant="body" tone="fg-3" className="text-[12px]">
        {k}
      </Text>
      <Text as="div" variant="mono" tone="fg-2" className="text-[12px]">
        {v}
      </Text>
    </div>
  )
}

function statusDotTone(s: StatusTone): "success" | "danger" | "neutral" {
  if (s === "pass") return "success"
  if (s === "fail") return "danger"
  return "neutral"
}
