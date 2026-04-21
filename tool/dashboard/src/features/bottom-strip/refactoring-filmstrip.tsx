import { RailSection } from "@/components/rail-section"
import type {
  CheckpointVM,
  RefactoringStepVM,
  Selection,
} from "@/data/types"
import { RefactoringCard } from "@/features/bottom-strip/refactoring-card"
import { useDashboardStore } from "@/stores/dashboard-store"

/**
 * Horizontal list of refactoring-step cards. Clicking a card sets the
 * global selection so the detail panel opens on that refactoring.
 */
export function RefactoringFilmstrip({
  steps,
  checkpoints,
}: {
  steps: RefactoringStepVM[]
  checkpoints: CheckpointVM[]
}) {
  const selection = useDashboardStore((s) => s.selection)
  const setSelection = useDashboardStore((s) => s.setSelection)

  return (
    <RailSection
      title={`Refactorings (${steps.length})`}
      className="flex h-full flex-col"
    >
      <div className="flex gap-1.5 overflow-x-auto px-3 pb-1">
        {steps.length === 0 ? (
          <span className="text-fg-4 px-1 py-3 text-[11px] italic">
            No refactorings detected in this session.
          </span>
        ) : (
          steps.map((s) => (
            <RefactoringCard
              key={s.index}
              step={s}
              checkpoint={checkpoints[s.checkpointIndex]}
              selected={isSelected(selection, s.index)}
              onClick={() => setSelection({ kind: "refactoring", index: s.index })}
            />
          ))
        )}
      </div>
    </RailSection>
  )
}

function isSelected(selection: Selection, index: number): boolean {
  return selection?.kind === "refactoring" && selection.index === index
}
