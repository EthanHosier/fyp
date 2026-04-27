import { AlertTriangleIcon, CheckIcon } from "lucide-react"

import { PitfallCallout } from "@/components/pitfall-callout"
import type { CodeSmellsVM, CodeSmellVM } from "@/data/types"

/**
 * Process-signal callouts derived from the checkpoint's smell trajectory:
 * one warning when any new violations were introduced, one positive when
 * any were resolved. Combined per direction so the section stays
 * skimmable — the per-violation breakdown lives in `CodeSmellsSection`.
 *
 * Returns a fragment (no outer section); `CheckpointBody` owns the
 * shared "Process Signals" header.
 */
export function SmellSignals({ smells }: { smells: CodeSmellsVM }) {
  const added = smells.added.length
  const resolved = smells.resolved.length
  if (added === 0 && resolved === 0) return null

  return (
    <>
      {added > 0 ? (
        <PitfallCallout
          tone="warn"
          icon={<AlertTriangleIcon className="size-4" strokeWidth={2} />}
          title={`You introduced ${added} new code smell${added === 1 ? "" : "s"}`}
          description={`This checkpoint introduced ${describeByFile(smells.added)}. See the Code Smells section below.`}
        />
      ) : null}
      {resolved > 0 ? (
        <PitfallCallout
          tone="good"
          icon={<CheckIcon className="size-2.5" strokeWidth={2.5} />}
          title={`You resolved ${resolved} code smell${resolved === 1 ? "" : "s"}`}
          description={`This checkpoint cleared ${describeByFile(smells.resolved)}. See the Code Smells section below.`}
        />
      ) : null}
    </>
  )
}

// Group smells by file (full path key, basename only at render time so
// two folders with the same filename stay as distinct groups), count
// per (file, rule), and render each rule as "<rule>" when it fires
// once or "<n> <rule>s" when it repeats. Groups joined with "; ".
function describeByFile(items: CodeSmellVM[]): string {
  const byFile = new Map<string, Map<string, number>>()
  for (const s of items) {
    const ruleCounts = byFile.get(s.file) ?? new Map<string, number>()
    ruleCounts.set(s.rule, (ruleCounts.get(s.rule) ?? 0) + 1)
    byFile.set(s.file, ruleCounts)
  }
  const groups: string[] = []
  for (const [file, ruleCounts] of byFile) {
    const parts: string[] = []
    for (const [rule, count] of ruleCounts) {
      parts.push(count === 1 ? rule : `${count} ${rule}s`)
    }
    groups.push(`${parts.join(", ")} in ${basename(file)}`)
  }
  return groups.join("; ")
}

function basename(path: string): string {
  const slash = path.lastIndexOf("/")
  return slash === -1 ? path : path.substring(slash + 1)
}
