/*
 * Detail-panel section listing CPD clone groups added or resolved at the
 * selected checkpoint. Mirrors `CodeSmellsSection` shape but only shows
 * churn — `carried` clones are intentionally not surfaced.
 */

import { DuplicationCard } from "@/components/duplication-card"
import { Text } from "@/components/text"
import type { DuplicationsVM } from "@/data/types"

export function DuplicationsSection({
  duplications,
  checkpointSha,
}: {
  duplications: DuplicationsVM
  checkpointSha: string
}) {
  const { added, resolved } = duplications
  const total = added.length + resolved.length

  return (
    <section className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-2">
        <Text as="h3" variant="eyebrow" tone="fg-4">
          Duplication
        </Text>
        {total > 0 ? (
          <Text
            as="span"
            variant="monoCaption"
            tone="fg-4"
            className="uppercase tracking-[0.08em]"
          >
            {added.length} new · {resolved.length} resolved
          </Text>
        ) : null}
      </div>
      {total === 0 ? (
        <Text variant="bodySm" tone="fg-4" className="italic">
          No duplication added or resolved at this checkpoint.
        </Text>
      ) : (
        <div className="grid grid-cols-[minmax(0,1fr)] gap-1.5">
          {added.map((g, i) => (
            <DuplicationCard
              key={`new/${checkpointSha}/${g.identity || i}`}
              group={g}
              cacheKey={`dup-new-${checkpointSha}-${g.identity || i}`}
            />
          ))}
          {resolved.map((g, i) => (
            <DuplicationCard
              key={`resolved/${checkpointSha}/${g.identity || i}`}
              group={g}
              cacheKey={`dup-resolved-${checkpointSha}-${g.identity || i}`}
            />
          ))}
        </div>
      )}
    </section>
  )
}
