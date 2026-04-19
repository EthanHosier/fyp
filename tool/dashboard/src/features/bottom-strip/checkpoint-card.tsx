import { StatusDot } from "@/components/status-dot"
import { Text } from "@/components/text"
import type { CheckpointVM, StatusTone } from "@/data/types"
import { cn } from "@/lib/utils"

/**
 * One card in the checkpoint filmstrip — status dot + cX label + tLabel
 * on the top row, a one-line description below. Selected state pulls in
 * a brand border + deeper background.
 */
export function CheckpointCard({
  checkpoint,
  selected,
  onClick,
}: {
  checkpoint: CheckpointVM
  selected: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      aria-label={`Select checkpoint ${checkpoint.label}`}
      onClick={onClick}
      className={cn(
        "flex w-[120px] shrink-0 cursor-pointer flex-col gap-1 rounded-sm border px-2 py-[7px] text-left transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
        selected
          ? "bg-bg-3 border-brand"
          : "bg-bg-2 border-border hover:border-border-strong",
      )}
    >
      <div className="flex items-center gap-1.5">
        <StatusDot tone={statusDotTone(checkpoint.status)} />
        <Text variant="mono" tone="fg" className="text-[11px]">
          {checkpoint.label}
        </Text>
        <Text variant="monoTiny" tone="fg-4">
          {checkpoint.tLabel}
        </Text>
      </div>
      <Text
        variant="caption"
        tone="fg-3"
        className="truncate text-[10.5px]"
      >
        {checkpoint.description}
      </Text>
    </button>
  )
}

function statusDotTone(status: StatusTone): "success" | "danger" | "neutral" {
  if (status === "pass") return "success"
  if (status === "fail") return "danger"
  return "neutral"
}
