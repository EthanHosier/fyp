import { Lightbulb } from "lucide-react"

import { RailSection } from "@/components/rail-section"
import { Text } from "@/components/text"
import { toViewModel } from "@/data/view-model"
import { HeaderBar } from "@/features/header/header-bar"
import { MetricRail } from "@/features/metric-rail/metric-rail"
import { useReport } from "@/hooks/useReport"

/*
 * Step 5 — static layout shell. Three regions:
 *   ┌──────────────────────────── Header ────────────────────────────┐
 *   │                                                                 │
 *   │ MetricRail │           Main (toolbar + chart + legend)          │
 *   │            │                                                    │
 *   │            ├────────────────────────────────────────────────────┤
 *   │            │                  BottomStrip                       │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 * Everything here is a placeholder block — real features land in later steps.
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
          <BottomStripPlaceholder />
        </main>
      </div>
    </div>
  )
}

function LoadingState() {
  return (
    <div className="flex h-screen w-screen items-center justify-center bg-bg">
      <Text variant="mono" tone="fg-4">waiting for analysis report…</Text>
    </div>
  )
}


function ChartToolbarPlaceholder() {
  return (
    <div className="flex items-center gap-3 px-1 pb-[10px] pt-1">
      <div>
        <Text as="div" variant="eyebrow" tone="fg-4">TRAJECTORY · PRIMARY</Text>
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
      <Text variant="mono" tone="fg-4">trajectory-chart · steps 10–12</Text>
    </div>
  )
}

function ChartLegendPlaceholder() {
  return (
    <Text as="div" variant="mono" tone="fg-4" className="flex items-center gap-4 px-1 pb-1 pt-[10px]">
      <span>primary ──</span>
      <span>overlays ╌╌</span>
      <span>intervals ▢</span>
    </Text>
  )
}

function BottomStripPlaceholder() {
  return (
    <div className="border-border bg-bg-1 grid h-[170px] shrink-0 grid-cols-[1fr_420px] border-t">
      <div className="border-border overflow-hidden border-r">
        <RailSection title="Checkpoints" description="Step 9 — filmstrip">
          <Placeholder lines={2} />
        </RailSection>
      </div>
      <ExplanationCardPlaceholder />
    </div>
  )
}

function ExplanationCardPlaceholder() {
  return (
    <div className="overflow-auto">
      <RailSection title="Why it matters" icon={<Lightbulb className="size-3 text-brand" />}>
        <Text as="p" variant="bodySm" tone="fg-3" className="px-3">
          Click a <Text as="strong" variant="bodySm" tone="fg-2" className="font-semibold">checkpoint</Text>, a{" "}
          <Text as="strong" variant="bodySm" tone="fg-2" className="font-semibold">build interval</Text>, or an{" "}
          <Text as="strong" variant="bodySm" tone="fg-2" className="font-semibold">annotation</Text>{" "}
          to see a narrated explanation here.
        </Text>
      </RailSection>
    </div>
  )
}

function Placeholder({ lines }: { lines: number }) {
  return (
    <div className="flex flex-col gap-1.5">
      {Array.from({ length: lines }).map((_, i) => (
        <div key={i} className="bg-bg-2 h-3 w-full rounded-sm" />
      ))}
    </div>
  )
}
