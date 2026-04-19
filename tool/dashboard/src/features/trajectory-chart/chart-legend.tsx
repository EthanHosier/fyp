import { LegendSwatch } from "@/components/legend-swatch"
import type { MetricVM } from "@/data/types"
import { TONE_BG } from "@/lib/metric-tone"
import { cn } from "@/lib/utils"

/**
 * Legend strip under the chart. For step 10 we only render the primary
 * swatch — secondaries + build/test interval markers get added in
 * steps 11–12.
 */
export function ChartLegend({ primary }: { primary: MetricVM }) {
  return (
    <div className="flex flex-wrap items-center gap-4 px-1 pt-[10px] pb-1">
      <LegendSwatch label={`primary · ${primary.label.toLowerCase()}`}>
        <span className={cn("inline-block h-[2px] w-[18px]", TONE_BG[primary.tone])} />
      </LegendSwatch>
    </div>
  )
}
