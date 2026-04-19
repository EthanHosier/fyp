import { ArrowLeft } from "lucide-react"
import { useNavigate } from "react-router-dom"

import { Text } from "@/components/text"
import { Button } from "@/components/ui/button"
import { toViewModel } from "@/data/view-model"
import { useReport } from "@/hooks/useReport"

/**
 * Debug page at `/data`. Side-by-side view of the raw `AnalysisReport`
 * coming out of `useReport()` and the `DashboardViewModel` produced by
 * `toViewModel()`. Useful when changing the mapping to eyeball the
 * before/after without opening the detail panel.
 */
export function DataApp() {
  const report = useReport()
  const vm = report ? toViewModel(report) : null
  const navigate = useNavigate()

  return (
    <div className="flex h-full w-full flex-col bg-bg text-fg">
      <header className="border-border bg-bg-1 flex h-12 shrink-0 items-center gap-3 border-b px-4">
        <Text variant="heading" tone="fg">Data inspector</Text>
        <Text variant="mono" tone="fg-3">
          useReport() · toViewModel()
        </Text>
        <div className="ml-auto">
          <Button variant="outline" size="sm" onClick={() => navigate("/")}>
            <ArrowLeft />
            <span>Back</span>
          </Button>
        </div>
      </header>
      <div className="grid min-h-0 flex-1 grid-cols-2 divide-x divide-border">
        <JsonPane title="Raw · AnalysisReport" value={report} />
        <JsonPane title="Parsed · DashboardViewModel" value={vm} />
      </div>
    </div>
  )
}

function JsonPane({ title, value }: { title: string; value: unknown }) {
  return (
    <section className="flex min-h-0 flex-col">
      <div className="border-border flex items-center justify-between border-b px-4 py-2">
        <Text variant="eyebrow" tone="fg-3">{title}</Text>
        <Text variant="monoCaption" tone="fg-4">
          {value == null ? "null" : `${JSON.stringify(value).length} chars`}
        </Text>
      </div>
      <pre className="min-h-0 flex-1 overflow-auto bg-bg px-4 py-3 font-mono text-[11px] leading-[1.5] text-fg-2">
        {value == null ? "(no report)" : JSON.stringify(value, null, 2)}
      </pre>
    </section>
  )
}
