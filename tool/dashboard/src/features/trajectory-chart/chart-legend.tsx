import { LegendSwatch } from "@/components/legend-swatch"
import { ToneChip } from "@/components/tone-chip"
import { Separator } from "@/components/ui/separator"
import type { MetricVM } from "@/data/types"
import { TONE_BG } from "@/lib/metric-tone"
import { cn } from "@/lib/utils"

/**
 * Legend strip under the chart. Primary swatch (solid line), one
 * dashed swatch per active overlay, and the build/test status key
 * when the interval rail is on.
 */
export function ChartLegend({
  primary,
  secondaries,
  showIntervals,
}: {
  primary: MetricVM
  secondaries: MetricVM[]
  showIntervals: boolean
}) {
  return (
    <div className="flex flex-wrap items-center gap-4 px-1 pt-[10px] pb-1">
      <LegendSwatch label={`primary · ${primary.label.toLowerCase()}`}>
        <span className={cn("inline-block h-[2px] w-[18px]", TONE_BG[primary.tone])} />
      </LegendSwatch>
      {secondaries.map((m) => (
        <LegendSwatch key={m.id} label={m.label.toLowerCase()}>
          <DashedSwatch tone={TONE_BG[m.tone]} />
        </LegendSwatch>
      ))}
      {showIntervals ? (
        <>
          <Separator orientation="vertical" className="h-3.5" />
          <LegendSwatch label="pass">
            <span className="bg-good/80 inline-block size-2.5 rounded-[2px]" />
          </LegendSwatch>
          <LegendSwatch label="fail">
            <span className="bg-bad/80 inline-block size-2.5 rounded-[2px]" />
          </LegendSwatch>
          <LegendSwatch label="skipped">
            <span className="bg-unknown/60 inline-block size-2.5 rounded-[2px]" />
          </LegendSwatch>
        </>
      ) : null}
      <Separator orientation="vertical" className="h-3.5" />
      <LegendSwatch label="process error">
        <ToneChip tone="bad" size={12} />
      </LegendSwatch>
      <LegendSwatch label="process warning">
        <ToneChip tone="warn" size={12} />
      </LegendSwatch>
    </div>
  )
}

function DashedSwatch({ tone }: { tone: string }) {
  return (
    <span className="inline-flex w-[18px] items-center justify-between">
      <span className={cn("h-[2px] w-[5px]", tone)} />
      <span className={cn("h-[2px] w-[5px]", tone)} />
      <span className={cn("h-[2px] w-[5px]", tone)} />
    </span>
  )
}
