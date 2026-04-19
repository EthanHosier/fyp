import { Meter } from "@/components/meter"

/**
 * Snapshot of a single metric at a point in time: uppercase label,
 * monospace value + unit, rainbow Meter showing where the value sits
 * within its min/max range.
 */
export function MetricTile({
  label,
  value,
  unit,
  fraction,
  better = "lower",
}: {
  label: string
  value: number | string
  unit?: string
  /** 0..1 — where the value sits in its range (min=0, max=1). */
  fraction: number
  better?: "lower" | "higher"
}) {
  return (
    <div className="flex flex-col gap-1.5 rounded-sm border border-border bg-bg-2 px-2.5 py-2">
      <div className="font-mono text-[10px] tracking-[0.06em] uppercase text-fg-4">
        {label}
      </div>
      <div className="font-mono text-[17px] leading-none text-fg">
        {value}
        {unit ? <span className="text-fg-4 ml-1 text-[11px]">{unit}</span> : null}
      </div>
      <Meter value={fraction} better={better} />
    </div>
  )
}
