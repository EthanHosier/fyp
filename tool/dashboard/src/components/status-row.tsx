import { CheckIcon, HelpCircleIcon, XIcon } from "lucide-react"
import { Badge } from "@/components/ui/badge"

export type StatusTone = "pass" | "fail" | "unknown"

const BADGE_VARIANT: Record<StatusTone, "success" | "danger" | "neutral"> = {
  pass: "success",
  fail: "danger",
  unknown: "neutral",
}

function StatusBadge({ tone, label }: { tone: StatusTone; label: string }) {
  const Icon = tone === "pass" ? CheckIcon : tone === "fail" ? XIcon : HelpCircleIcon
  return (
    <Badge variant={BADGE_VARIANT[tone]}>
      <Icon />
      {label}
    </Badge>
  )
}

/**
 * Pair of status badges summarising a checkpoint's build + tests result.
 */
export function StatusRow({
  build,
  tests,
}: {
  build: StatusTone
  tests: StatusTone
}) {
  return (
    <div className="flex flex-wrap gap-1.5">
      <StatusBadge tone={build} label={`build ${build}`} />
      <StatusBadge tone={tests} label={`tests ${tests}`} />
    </div>
  )
}
