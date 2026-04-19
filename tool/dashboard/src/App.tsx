import { Text } from "@/components/text"
import { toViewModel } from "@/data/view-model"
import { BottomStrip } from "@/features/bottom-strip/bottom-strip"
import { HeaderBar } from "@/features/header/header-bar"
import { MetricRail } from "@/features/metric-rail/metric-rail"
import { TrajectoryChart } from "@/features/trajectory-chart/trajectory-chart"
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
            <TrajectoryChart vm={vm} />
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

