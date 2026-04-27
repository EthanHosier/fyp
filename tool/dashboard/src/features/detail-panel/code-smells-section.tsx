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

import { CodeSmellCard, type CodeSmellState } from "@/components/code-smell-card"
import { Text } from "@/components/text"
import type { CodeSmellVM, CodeSmellsVM } from "@/data/types"

type Row = { vm: CodeSmellVM; state: CodeSmellState }

export function CodeSmellsSection({
  smells,
  checkpointSha,
}: {
  smells: CodeSmellsVM
  checkpointSha: string
}) {
  const { added, carried, resolved, totalNow } = smells

  // News first (so the user lands on what just changed), then carried,
  // then resolved trailing — they're historical and dimmed inline.
  const rows: Row[] = [
    ...added.map((vm) => ({ vm, state: "new" as CodeSmellState })),
    ...carried.map((vm) => ({ vm, state: "carried" as CodeSmellState })),
    ...resolved.map((vm) => ({ vm, state: "resolved" as CodeSmellState })),
  ]

  return (
    <section className="flex flex-col gap-2">
      <Text as="h3" variant="eyebrow" tone="fg-4">
        Code smells ({totalNow})
      </Text>
      {rows.length === 0 ? (
        <Text variant="bodySm" tone="fg-4" className="italic">
          No smells flagged at this checkpoint.
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

function cardKey(
  checkpointSha: string,
  state: CodeSmellState,
  s: CodeSmellVM,
  index: number,
): string {
  return `${checkpointSha}/${state}/${s.file}:${s.beginLine}/${s.rule}/${index}`
}
