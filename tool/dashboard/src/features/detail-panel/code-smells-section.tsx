/*
 * Detail-panel section listing every PMD violation relevant to the
 * selected checkpoint as a flat list. Ordering is trajectory-aware:
 * violations first seen at this checkpoint surface at the top, carried
 * follow, resolved-since-prev sit at the bottom in a muted state.
 * Each card's `state` pill (NEW / RESOLVED) plus the resolved-only
 * opacity wash differentiates the three groups inline so the user gets
 * the same narrative without sub-headers.
 *
 * Renders inline with the rest of the panel (no inner scroll). The
 * collapsed `CodeSmellCard` is a single button with no Shiki/patch
 * parsing, so 100+ flagged violations stay cheap until the user
 * expands one.
 */

import { useState } from "react"

import { CodeSmellCard, type CodeSmellState } from "@/components/code-smell-card"
import { Text } from "@/components/text"
import type { CodeSmellVM, CodeSmellsVM } from "@/data/types"
import { cn } from "@/lib/utils"

type Row = { vm: CodeSmellVM; state: CodeSmellState }

export function CodeSmellsSection({
  smells,
  checkpointSha,
  touchedFiles,
}: {
  smells: CodeSmellsVM
  checkpointSha: string
  /** Files touched by this checkpoint's patch (repo-relative paths from
   * git's diff output). Used to filter the default view to "smells in
   * files this checkpoint actually touched". Empty list disables the
   * filter (e.g. seed checkpoint with no diff). */
  touchedFiles: string[]
}) {
  const { added, carried, resolved } = smells
  const [showAll, setShowAll] = useState(false)

  // News first (so the user lands on what just changed), then carried,
  // then resolved trailing — they're historical and dimmed inline.
  // Within each group, sort by file alphabetically then line number so
  // smells appear in source order within each file.
  const allRows: Row[] = [
    ...sortByFileThenLine(added).map((vm) => ({ vm, state: "new" as CodeSmellState })),
    ...sortByFileThenLine(carried).map((vm) => ({ vm, state: "carried" as CodeSmellState })),
    ...sortByFileThenLine(resolved).map((vm) => ({ vm, state: "resolved" as CodeSmellState })),
  ]

  const canFilter = touchedFiles.length > 0
  const touchedRows = canFilter
    ? allRows.filter((r) => isTouched(r.vm.file, touchedFiles))
    : allRows
  const rows = canFilter && !showAll ? touchedRows : allRows
  const hiddenCount = allRows.length - touchedRows.length

  return (
    <section className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-2">
        <Text as="h3" variant="eyebrow" tone="fg-4">
          Code smells
        </Text>
        {canFilter && hiddenCount > 0 ? (
          <button
            type="button"
            onClick={() => setShowAll((v) => !v)}
            className={cn(
              "text-fg-4 hover:text-fg-2 text-[10px] font-medium uppercase tracking-wider",
            )}
          >
            {showAll ? `Touched only (${touchedRows.length})` : `Show all (+${hiddenCount})`}
          </button>
        ) : null}
      </div>
      {rows.length === 0 ? (
        <Text variant="bodySm" tone="fg-4" className="italic">
          {canFilter && !showAll && allRows.length > 0
            ? "No smells in files touched by this checkpoint."
            : "No smells flagged at this checkpoint."}
        </Text>
      ) : (
        // Grid with `minmax(0, 1fr)` instead of a flex column — same
        // trick `DiffSection` uses to keep wide card content (long
        // source lines, long PMD rule names) from blowing the sidebar
        // out. `min-w-0` on a flex-col child doesn't constrain
        // horizontal width the same way; the grid's explicit
        // zero-min track does.
        <div className="grid grid-cols-[minmax(0,1fr)] gap-1.5">
          {rows.map(({ vm, state }, i) => {
            const key = cardKey(checkpointSha, state, vm, i)
            return (
              <CodeSmellCard
                key={key}
                state={state}
                rule={vm.rule}
                ruleSet={vm.ruleSet}
                priority={vm.priority}
                file={vm.file}
                beginLine={vm.beginLine}
                endLine={vm.endLine}
                message={vm.message}
                patch={vm.snippetPatch}
                cacheKey={key}
                defaultOpen={false}
              />
            )
          })}
        </div>
      )}
    </section>
  )
}

// Smell paths come from PMD (often absolute), patch paths come from git
// (repo-relative). Match either direction with a path-separator boundary
// so "Foo.java" doesn't accidentally match "MyFoo.java".
function isTouched(smellFile: string, touched: string[]): boolean {
  for (const t of touched) {
    if (smellFile === t) return true
    if (smellFile.endsWith("/" + t)) return true
    if (t.endsWith("/" + smellFile)) return true
  }
  return false
}

function sortByFileThenLine(items: CodeSmellVM[]): CodeSmellVM[] {
  return [...items].sort((a, b) => {
    const f = a.file.localeCompare(b.file)
    if (f !== 0) return f
    return a.beginLine - b.beginLine
  })
}

function cardKey(
  checkpointSha: string,
  state: CodeSmellState,
  s: CodeSmellVM,
  index: number,
): string {
  return `${checkpointSha}/${state}/${s.file}:${s.beginLine}/${s.rule}/${index}`
}
