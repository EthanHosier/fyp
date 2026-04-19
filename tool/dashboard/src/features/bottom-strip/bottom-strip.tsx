import type { DashboardViewModel } from "@/data/types"
import { CheckpointFilmstrip } from "@/features/bottom-strip/checkpoint-filmstrip"
import { ExplanationCard } from "@/features/bottom-strip/explanation-card"

/**
 * Bottom region: filmstrip of checkpoints on the left, delta-bullet
 * "why it matters" card on the right (driven by the current selection).
 */
export function BottomStrip({ vm }: { vm: DashboardViewModel }) {
  return (
    <div className="border-border bg-bg-1 grid min-h-[150px] shrink-0 grid-cols-[1fr_420px] items-stretch border-t">
      <div className="border-border flex flex-col overflow-hidden border-r">
        <CheckpointFilmstrip checkpoints={vm.checkpoints} />
      </div>
      <ExplanationCard vm={vm} />
    </div>
  )
}
