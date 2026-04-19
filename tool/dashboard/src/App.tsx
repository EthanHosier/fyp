import { useReport } from "@/hooks/useReport"

export function App() {
  const report = useReport()

  if (!report) {
    return (
      <div className="flex min-h-svh items-center p-6 text-sm text-muted-foreground">
        Waiting for analysis report…
      </div>
    )
  }

  return (
    <div className="flex min-h-svh flex-col gap-4 p-6 text-sm">
      <header>
        <h1 className="text-lg font-medium">Refactoring Dashboard</h1>
        <p className="text-muted-foreground">
          Session <code>{report.session.sessionId}</code> ·{" "}
          {report.checkpoints.length} checkpoint(s) ·{" "}
          {report.trajectory?.numSteps ?? 0} step(s)
        </p>
      </header>

      <pre className="max-h-[70vh] overflow-auto rounded bg-muted p-3 font-mono text-xs">
        {JSON.stringify(report, null, 2)}
      </pre>
    </div>
  )
}

export default App
