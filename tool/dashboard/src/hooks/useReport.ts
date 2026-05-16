import { useEffect, useState } from "react"

import type { AnalysisReport } from "@/generated/report-types"
import reportData from "./analysis-report.json"

const USE_HARDCODED_REPORT = false

declare global {
  interface Window {
    __REPORT__?: AnalysisReport
  }
}

const REPORT_EVENT = "refdash:report-loaded"

/**
 * Reads the analysis report injected by the IntelliJ plugin as
 * `window.__REPORT__`. The plugin fires a `refdash:report-loaded`
 * CustomEvent after assigning the global, which may land before or
 * after this hook mounts — we cover both by seeding state from
 * `window.__REPORT__` and re-reading it from the listener.
 *
 * For local dev without the plugin, swap the import/return below for
 * the `analysis-report.json` bundled next to this file.
 */
export function useReport(): AnalysisReport | null {
  // Initialiser reads `window.__REPORT__` synchronously, so a payload
  // already injected by the plugin before this hook mounts is captured
  // here and the effect below never has to run.
  const [report, setReport] = useState<AnalysisReport | null>(
    () => window.__REPORT__ ?? null,
  )

  useEffect(() => {
    if (report != null) return
    const handler = () => {
      if (window.__REPORT__) setReport(window.__REPORT__)
    }
    window.addEventListener(REPORT_EVENT, handler)
    return () => window.removeEventListener(REPORT_EVENT, handler)
  }, [report])

  return USE_HARDCODED_REPORT ? (reportData as AnalysisReport) : report
}
