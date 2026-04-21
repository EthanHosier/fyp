import type { DashboardViewModel } from "@/data/types"
import { ExplanationCard } from "@/features/bottom-strip/explanation-card"
import { RefactoringFilmstrip } from "@/features/bottom-strip/refactoring-filmstrip"

/**
 * Bottom region: filmstrip of refactoring steps on the left, delta-bullet
 * "what changed" card on the right (driven by the current selection).
 */
export function BottomStrip({ vm }: { vm: DashboardViewModel }) {
  return (
    <div className="border-border bg-bg-1 grid min-h-[150px] shrink-0 grid-cols-[1fr_420px] items-stretch border-t">
      <div className="border-border flex flex-col overflow-hidden border-r">
        <RefactoringFilmstrip steps={vm.refactoringSteps} checkpoints={vm.checkpoints} />
      </div>
      <ExplanationCard vm={vm} />
    </div>
  )
}
