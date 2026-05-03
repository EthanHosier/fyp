/*
 * Detail-panel section listing CPD clone groups newly introduced at the
 * selected checkpoint. Resolved groups are intentionally suppressed
 * because the body-hash identity is fragile under indentation changes
 * (Extract Method etc.) — surfacing resolved+new together would
 * misrepresent the same logical clone as both gone and reintroduced.
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
  const { added } = duplications
  if (added.length === 0) return null

  return (
    <section className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-2">
        <Text as="h3" variant="eyebrow" tone="fg-4">
          Duplication
        </Text>
        <Text
          as="span"
          variant="monoCaption"
          tone="fg-4"
          className="uppercase tracking-[0.08em]"
        >
          {added.length} new
        </Text>
      </div>
      <div className="grid grid-cols-[minmax(0,1fr)] gap-1.5">
        {added.map((g, i) => (
          <DuplicationCard
            key={`new/${checkpointSha}/${g.identity || i}`}
            group={g}
            cacheKey={`dup-new-${checkpointSha}-${g.identity || i}`}
          />
        ))}
      </div>
    </section>
  )
}
