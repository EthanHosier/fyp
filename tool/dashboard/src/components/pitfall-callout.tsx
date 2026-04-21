import { ChevronDownIcon, ChevronUpIcon } from "lucide-react"
import { useState, type ReactNode } from "react"

import { Text } from "@/components/text"
import { cn } from "@/lib/utils"

/**
 * Tinted rounded card highlighting a pitfall attached to a refactoring
 * — used in the detail panel for "tests not run" and "IDE could have
 * done this". Visual port of the reference `SignalsBlock` items: tone-
 * tinted background + border, 18×18 icon chip on the left, title +
 * optional mono subtitle + description on the right.
 *
 * Collapsible: title (+ mono subline) stay visible; the description
 * expands/collapses when the card is clicked. `defaultOpen` controls
 * the initial state.
 */
type PitfallTone = "bad" | "warn"

type PitfallCalloutProps = {
  tone: PitfallTone
  icon: ReactNode
  title: string
  /** Optional monospaced subline — e.g. the IDE action name. */
  mono?: string
  description: string
  defaultOpen?: boolean
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
  defaultOpen = false,
  className,
}: PitfallCalloutProps) {
  const [open, setOpen] = useState(defaultOpen)
  const Chevron = open ? ChevronUpIcon : ChevronDownIcon

  return (
    <div
      className={cn(
        "rounded-[5px] border",
        TONE_SURFACE[tone],
        className,
      )}
    >
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex w-full items-start gap-2.5 px-2.5 py-2 text-left"
      >
        <div
          className={cn(
            "mt-0.5 grid size-[18px] shrink-0 place-items-center rounded-full border text-[11px] font-semibold",
            TONE_CHIP[tone],
          )}
        >
          {icon}
        </div>
        <Text
          as="span"
          variant="body"
          className={cn("min-w-0 flex-1 text-[12px] font-semibold", TONE_TITLE[tone])}
        >
          {title}
        </Text>
        <Chevron className="text-fg-4 mt-0.5 size-3 shrink-0" />
      </button>
      {open ? (
        <div className="flex flex-col gap-1 px-2.5 pb-2 pl-[38px]">
          {mono ? (
            <Text variant="mono" tone="fg-2" className="text-[11.5px]">
              {mono}
            </Text>
          ) : null}
          <Text variant="bodySm" tone="fg-3" className="leading-[1.5]">
            {description}
          </Text>
        </div>
      ) : null}
    </div>
  )
}
