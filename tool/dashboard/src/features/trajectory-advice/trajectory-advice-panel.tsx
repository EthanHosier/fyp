import { CheckCircle2, Lightbulb } from "lucide-react"

import { Text } from "@/components/text"
import type { AdviceItemVM, DashboardViewModel } from "@/data/types"
import { cn } from "@/lib/utils"

/**
 * Trajectory-wide observations from the backend's `TrajectoryAdvisor`.
 * Each row is one rule that fired, with a severity icon, a headline,
 * and 1–3 sentences explaining what it saw. Pure presentational —
 * never tied to selection state.
 *
 * Empty state shows a "clean session" confirmation rather than hiding
 * the panel, so the user knows the analysis ran and just had nothing
 * to flag.
 */
export function TrajectoryAdvicePanel({ vm }: { vm: DashboardViewModel }) {
  const items = [...vm.advice].sort(
    (a, b) => SEVERITY_RANK[a.severity] - SEVERITY_RANK[b.severity],
  )
  return (
    <section className="px-5 py-4">
      <div className="mb-3 flex items-center gap-1.5">
        <Lightbulb className="size-3 text-brand" />
        <Text variant="eyebrow" tone="fg-4">
          Session takeaways
        </Text>
        {items.length > 0 ? (
          <Text variant="mono" tone="fg-4">
            ({items.length})
          </Text>
        ) : null}
      </div>
      {items.length === 0 ? (
        <EmptyState />
      ) : (
        <ul className="flex flex-col">
          {items.map((it, i) => (
            <AdviceRow key={it.kind} item={it} first={i === 0} />
          ))}
        </ul>
      )}
    </section>
  )
}

function AdviceRow({ item, first }: { item: AdviceItemVM; first: boolean }) {
  const tone = TONE[item.severity]
  return (
    <li
      className={cn(
        "grid grid-cols-[110px_1fr] items-start gap-4 py-3",
        !first && "border-border border-t",
      )}
    >
      <Text variant="eyebrow" className={cn("pt-0.5", tone.label)}>
        {item.severity}
      </Text>
      <Text variant="bodySm" tone="fg-2">
        <span className="font-semibold text-fg-1">{item.title}.</span>{" "}
        {item.body}
      </Text>
    </li>
  )
}

function EmptyState() {
  return (
    <div className="flex items-center gap-2">
      <CheckCircle2 className="size-4 text-good" />
      <Text variant="bodySm" tone="fg-3">
        Nothing to flag — clean session.
      </Text>
    </div>
  )
}

const TONE: Record<AdviceItemVM["severity"], { label: string }> = {
  INFO: { label: "text-brand-2" },
  WARNING: { label: "text-warn" },
  CRITICAL: { label: "text-bad" },
}

const SEVERITY_RANK: Record<AdviceItemVM["severity"], number> = {
  CRITICAL: 0,
  WARNING: 1,
  INFO: 2,
}
