import { useEffect, useState } from "react"

import type { AnalysisReport } from "@/generated/report-types"
import reportData from "./analysis-report.json"

const USE_HARDCODED_REPORT = false

// Injected by vite.config.ts: true when the dev server was started with
// `--report=/path/to/analysis-report.json`. In that case useReport
// fetches /__dev_report.json (served by the dev middleware) and
// short-circuits the plugin path.
declare const __DEV_REPORT_AVAILABLE__: boolean

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
 * For local dev without the plugin, two options:
 *  1. Set [USE_HARDCODED_REPORT] = true to bundle the file at
 *     `analysis-report.json` next to this hook.
 *  2. Start vite with `REFDASH_REPORT=/path/to/file.json npm run dev`.
 *     The vite config registers a dev-only middleware at
 *     `/__dev_report.json` that serves that file from disk; this hook
 *     fetches it on mount when `__DEV_REPORT_AVAILABLE__` is true.
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

  // Dev-server `--report=...` path: fetch from the middleware.
  useEffect(() => {
    if (typeof __DEV_REPORT_AVAILABLE__ === "undefined" || !__DEV_REPORT_AVAILABLE__) return
    if (report != null) return
    let cancelled = false
    fetch("/__dev_report.json")
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`status ${r.status}`))))
      .then((data: AnalysisReport) => {
        if (!cancelled) setReport(data)
      })
      .catch((e) => {
        console.error("[useReport] failed to load dev report:", e)
      })
    return () => {
      cancelled = true
    }
  }, [report])

  return USE_HARDCODED_REPORT ? (reportData as AnalysisReport) : report
}
