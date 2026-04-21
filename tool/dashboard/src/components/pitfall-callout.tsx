import type { ReactNode } from "react"

import { Text } from "@/components/text"
import { cn } from "@/lib/utils"

/**
 * Tinted rounded card highlighting a pitfall attached to a refactoring
 * — used in the detail panel for "tests not run" and "IDE could have
 * done this". Visual port of the reference `SignalsBlock` items: tone-
 * tinted background + border, 18×18 icon chip on the left, title +
 * optional mono subtitle + description on the right.
 */
type PitfallTone = "bad" | "warn"

type PitfallCalloutProps = {
  tone: PitfallTone
  icon: ReactNode
  title: string
  /** Optional monospaced subline — e.g. the IDE action name. */
  mono?: string
  description: string
  className?: string
}

const TONE_SURFACE: Record<PitfallTone, string> = {
  bad: "border-bad/35 bg-bad/[0.08]",
  warn: "border-warn/35 bg-warn/[0.08]",
}

const TONE_CHIP: Record<PitfallTone, string> = {
  bad: "border-bad bg-bad/20 text-bad",
  warn: "border-warn bg-warn/20 text-warn",
}

const TONE_TITLE: Record<PitfallTone, string> = {
  bad: "text-bad",
  warn: "text-warn",
}

export function PitfallCallout({
  tone,
  icon,
  title,
  mono,
  description,
  className,
}: PitfallCalloutProps) {
  return (
    <div
      className={cn(
        "flex items-start gap-2.5 rounded-[5px] border px-2.5 py-2",
        TONE_SURFACE[tone],
        className,
      )}
    >
      <div
        className={cn(
          "mt-0.5 grid size-[18px] shrink-0 place-items-center rounded-full border text-[11px] font-semibold",
          TONE_CHIP[tone],
        )}
      >
        {icon}
      </div>
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <Text
          as="span"
          variant="body"
          className={cn("text-[12px] font-semibold", TONE_TITLE[tone])}
        >
          {title}
        </Text>
        {mono ? (
          <Text variant="mono" tone="fg-2" className="text-[11.5px]">
            {mono}
          </Text>
        ) : null}
        <Text variant="bodySm" tone="fg-3" className="leading-[1.5]">
          {description}
        </Text>
      </div>
    </div>
  )
}
