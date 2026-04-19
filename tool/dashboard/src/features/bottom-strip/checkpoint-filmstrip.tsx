import { RailSection } from "@/components/rail-section"
import type { CheckpointVM, Selection } from "@/data/types"
import { CheckpointCard } from "@/features/bottom-strip/checkpoint-card"
import { useDashboardStore } from "@/stores/dashboard-store"

/**
 * Horizontal list of checkpoint cards. Clicking a card sets the global
 * selection so the detail panel (step 13) opens on that checkpoint.
 */
export function CheckpointFilmstrip({
  checkpoints,
}: {
  checkpoints: CheckpointVM[]
}) {
  const selection = useDashboardStore((s) => s.selection)
  const setSelection = useDashboardStore((s) => s.setSelection)

  return (
    <RailSection title={`Checkpoints (${checkpoints.length})`}>
      <div className="flex gap-1.5 overflow-x-auto px-3 pb-1">
        {checkpoints.map((c) => (
          <CheckpointCard
            key={c.index}
            checkpoint={c}
            selected={isSelected(selection, c.index)}
            onClick={() => setSelection({ kind: "checkpoint", index: c.index })}
          />
        ))}
      </div>
    </RailSection>
  )
}

function isSelected(selection: Selection, index: number): boolean {
  return selection?.kind === "checkpoint" && selection.index === index
}
