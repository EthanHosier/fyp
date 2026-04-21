import { StatusDot } from "@/components/status-dot"
import { Text } from "@/components/text"
import type { CheckpointVM, RefactoringStepVM, StatusTone } from "@/data/types"
import { cn } from "@/lib/utils"

/**
 * One card in the refactoring-step filmstrip — status dot (from the
 * step's destination checkpoint) + refactoring type + tLabel on the top
 * row, with the RefactoringMiner description truncated below.
 */
export function RefactoringCard({
  step,
  checkpoint,
  selected,
  onClick,
}: {
  step: RefactoringStepVM
  checkpoint: CheckpointVM | undefined
  selected: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      aria-label={`Select refactoring ${step.refactoringType} at ${step.tLabel}`}
      onClick={onClick}
      className={cn(
        "flex w-[160px] shrink-0 cursor-pointer flex-col gap-1 rounded-sm border px-2 py-[7px] text-left transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
        selected
          ? "bg-bg-3 border-brand"
          : "bg-bg-2 border-border hover:border-border-strong",
      )}
    >
      <div className="flex items-center gap-1.5">
        <StatusDot tone={statusDotTone(checkpoint?.status ?? "unknown")} />
        <Text
          variant="mono"
          tone="fg"
          className="truncate text-[11px]"
        >
          {step.refactoringType}
        </Text>
      </div>
      <Text variant="monoTiny" tone="fg-4">
        {step.tLabel}
      </Text>
    </button>
  )
}

function statusDotTone(status: StatusTone): "success" | "danger" | "neutral" {
  if (status === "pass") return "success"
  if (status === "fail") return "danger"
  return "neutral"
}
