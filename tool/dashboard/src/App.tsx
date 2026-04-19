import { Text } from "@/components/text"
import { toViewModel } from "@/data/view-model"
import { BottomStrip } from "@/features/bottom-strip/bottom-strip"
import { HeaderBar } from "@/features/header/header-bar"
import { MetricRail } from "@/features/metric-rail/metric-rail"
import { useReport } from "@/hooks/useReport"

/*
 * Three-region shell. Real features: header bar (step 7), metric rail
 * (step 8), bottom strip (step 9). Chart region (steps 10–12) and
 * detail panel (steps 13+) still pending.
 *
 *   ┌──────────────────────────── Header ────────────────────────────┐
 *   │ MetricRail │           Main (toolbar + chart + legend)          │
 *   │            ├────────────────────────────────────────────────────┤
 *   │            │                  BottomStrip                       │
 *   └────────────────────────────────────────────────────────────────┘
 */
export default function App() {
  const report = useReport()
  const vm = report ? toViewModel(report) : null

  if (!vm) return <LoadingState />

  return (
    <div className="flex h-screen w-screen flex-col bg-bg text-fg">
      <HeaderBar vm={vm} />
      <div className="relative flex min-h-0 flex-1">
        <MetricRail vm={vm} />
        <main className="flex min-w-0 flex-1 flex-col overflow-hidden">
          <section className="min-h-0 flex-1 overflow-auto px-5 pt-[14px] pb-1">
            <ChartToolbarPlaceholder />
            <ChartPlaceholder />
            <ChartLegendPlaceholder />
          </section>
          <BottomStrip vm={vm} />
        </main>
      </div>
    </div>
  )
}

function LoadingState() {
  return (
    <div className="flex h-screen w-screen items-center justify-center bg-bg">
      <Text variant="mono" tone="fg-4">
        waiting for analysis report…
      </Text>
    </div>
  )
}

function ChartToolbarPlaceholder() {
  return (
    <div className="flex items-center gap-3 px-1 pb-[10px] pt-1">
      <div>
        <Text as="div" variant="eyebrow" tone="fg-4">
          TRAJECTORY · PRIMARY
        </Text>
        <Text as="div" variant="display" tone="fg" className="mt-0.5">
          Chart toolbar — step 10
        </Text>
      </div>
    </div>
  )
}

function ChartPlaceholder() {
  return (
    <div className="border-border bg-bg-1 flex h-[360px] items-center justify-center rounded-md border px-[10px] pb-2 pt-[6px]">
      <Text variant="mono" tone="fg-4">
        trajectory-chart · steps 10–12
      </Text>
    </div>
  )
}

function ChartLegendPlaceholder() {
  return (
    <Text
      as="div"
      variant="mono"
      tone="fg-4"
      className="flex items-center gap-4 px-1 pb-1 pt-[10px]"
    >
      <span>primary ──</span>
      <span>overlays ╌╌</span>
      <span>intervals ▢</span>
    </Text>
  )
}
