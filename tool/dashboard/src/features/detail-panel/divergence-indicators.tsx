import { Text } from "@/components/text"
import type {
  AlternativeTrajectoryVM,
  DashboardViewModel,
  DivergencePointVM,
} from "@/data/types"
import { useDashboardStore } from "@/stores/dashboard-store"
import { cn } from "@/lib/utils"

const GLYPH_BY_KIND: Record<DivergencePointVM["kind"], string> = {
  ORDERING: "⇄",
  IDE_REPLAY: "⚙",
  REWORK: "↻",
  HYGIENE: "⚠",
}

const KIND_LABEL: Record<DivergencePointVM["kind"], string> = {
  ORDERING: "Reorder",
  IDE_REPLAY: "IDE replay",
  REWORK: "Rework",
  HYGIENE: "Hygiene",
}

/**
 * Renders all [DivergencePointVM]s anchored at the refactoring step(s)
 * that landed at the given checkpoint as a stack of clickable cards.
 * Each card opens the corresponding alternative trajectory's detail card
 * so the user can drill into the counterfactual's stats. Returns null
 * when no DPs anchor here so the surrounding layout doesn't render an
 * empty section.
 *
 * `dp.stepIndex` in the report is a refactoring-step index (0..n-1
 * across the chronological refactoring list), NOT a checkpoint index.
 * The two differ because (a) refactoring steps don't exist on every
 * checkpoint (only on the ones a refactor lands on), and (b) a single
 * checkpoint can be the landing for multiple steps. We resolve the
 * mapping from the view-model's refactoringSteps and filter DPs whose
 * stepIndex matches any of the steps landing on this checkpoint.
 */
export function DivergenceIndicators({
  vm,
  checkpointIndex,
}: {
  vm: DashboardViewModel
  checkpointIndex: number
}) {
  const setSelection = useDashboardStore((s) => s.setSelection)
  const stepIndexes = new Set(
    vm.refactoringSteps
      .filter((s) => s.checkpointIndex === checkpointIndex)
      .map((s) => s.index),
  )
  const dps = vm.divergencePoints.filter((dp) => stepIndexes.has(dp.stepIndex))
  if (dps.length === 0) return null

  return (
    <section className="flex flex-col gap-2">
      <Text as="h3" variant="eyebrow" tone="fg-4">
        Divergence points
      </Text>
      <div className="flex flex-col gap-1.5">
        {dps.map((dp) => {
          const bestAlt = pickBestAlt(vm, dp)
          const onClick = () => {
            if (bestAlt) {
              setSelection({ kind: "alternative", index: bestAlt.index })
            }
          }
          return (
            <button
              key={dp.id}
              onClick={onClick}
              disabled={!bestAlt}
              className={cn(
                "border-border bg-bg-2 flex w-full min-w-0 flex-col gap-1 overflow-hidden rounded-md border px-3 py-2 text-left break-words transition-colors",
                bestAlt ? "hover:bg-bg-3 cursor-pointer" : "cursor-default opacity-60",
              )}
            >
              <span className="flex items-center gap-2">
                <span className="text-brand-2 text-base leading-none">
                  {GLYPH_BY_KIND[dp.kind]}
                </span>
                <Text variant="eyebrow" tone="fg-4">
                  {KIND_LABEL[dp.kind]}
                </Text>
                <span className="ml-auto">
                  <Text variant="mono" tone="fg-3">
                    {formatMagnitude(dp)}
                  </Text>
                </span>
              </span>
              <Text variant="body" tone="fg">
                {dp.title}
              </Text>
              <Text variant="mono" tone="fg-4">
                {dp.explanation}
              </Text>
            </button>
          )
        })}
      </div>
    </section>
  )
}

/** Pick the highest-process alt the DP owns; falls back to the first. */
function pickBestAlt(
  vm: DashboardViewModel,
  dp: DivergencePointVM,
): AlternativeTrajectoryVM | undefined {
  let best: AlternativeTrajectoryVM | undefined
  let bestScore = -Infinity
  for (const i of dp.altIndexes) {
    const alt = vm.alternativeTrajectories[i]
    if (!alt) continue
    const final =
      alt.continuationSteps.length > 0
        ? alt.continuationSteps[alt.continuationSteps.length - 1].processScore
        : alt.altValues.process
    const score = typeof final === "number" ? final : -Infinity
    if (score > bestScore) {
      best = alt
      bestScore = score
    }
  }
  return best
}

function formatMagnitude(dp: DivergencePointVM): string {
  if (dp.kind === "REWORK") {
    return `${dp.reworkLineCount ?? dp.magnitude} lines`
  }
  return `+${dp.magnitude.toFixed(1)} pts`
}
