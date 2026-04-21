import { StatusDot } from "@/components/status-dot"
import { Text } from "@/components/text"
import type { StatusTone } from "@/data/types"
import { formatDurationShort } from "@/lib/format"

/**
 * Compact body for interval selections that came from the build / tests
 * rail below the chart — shows only the run's total duration and
 * status, since the header already carries the "Build pass" / "Tests
 * skipped" title. Metric deltas live on the full `IntervalBody` used
 * for chart edge clicks.
 */
export function StatusIntervalBody({
  durationMs,
  status,
  statusLabel,
}: {
  durationMs: number
  status: StatusTone
  statusLabel: string
}) {
  return (
    <div className="flex flex-col gap-4">
      <section className="flex flex-col gap-2">
        <Text as="h3" variant="eyebrow" tone="fg-4">
          Details
        </Text>
        <KeyValue k="Duration" v={formatDurationShort(durationMs)} />
        <KeyValue
          k="Status"
          v={
            <span className="inline-flex items-center gap-1.5">
              <StatusDot tone={statusDotTone(status)} />
              <Text variant="mono" tone="fg-2">
                {statusLabel}
              </Text>
            </span>
          }
        />
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
