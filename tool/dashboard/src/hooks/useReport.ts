import { useEffect, useState } from "react"
import type { AnalysisReport } from "@/generated/report-types"

declare global {
  interface Window {
    __REPORT__?: AnalysisReport
  }
}

const REPORT_EVENT = "refdash:report-loaded"

/**
 * Returns the analysis report injected by the plugin, or `null` while
 * waiting. The plugin sets `window.__REPORT__` and dispatches
 * `refdash:report-loaded` after the page finishes loading — we handle both
 * orderings (set-before-mount and set-after-mount).
 */
export function useReport(): AnalysisReport | null {
  const [report, setReport] = useState<AnalysisReport | null>(
    () => window.__REPORT__ ?? null,
  )

  useEffect(() => {
    if (report != null) return
    const handler = () => {
      if (window.__REPORT__) setReport(window.__REPORT__)
    }
    window.addEventListener(REPORT_EVENT, handler)
    // Handle the case where the plugin injected after initial render but
    // before the listener attached.
    if (window.__REPORT__) setReport(window.__REPORT__)
    return () => window.removeEventListener(REPORT_EVENT, handler)
  }, [report])

  return report
}
