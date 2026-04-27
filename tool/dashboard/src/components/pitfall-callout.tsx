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
type PitfallTone = "bad" | "warn" | "good"

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
  good: "border-good/35 bg-good/[0.08]",
}

const TONE_CHIP: Record<PitfallTone, string> = {
  bad: "border-bad bg-bad/20 text-bad",
  warn: "border-warn bg-warn/20 text-warn",
  good: "border-good bg-good/20 text-good",
}

const TONE_TITLE: Record<PitfallTone, string> = {
  bad: "text-bad",
  warn: "text-warn",
  good: "text-good",
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
        className="flex w-full items-center gap-2.5 px-2.5 py-2 text-left"
      >
        {tone === "warn" ? (
          // `warn` renders a hand-drawn rounded triangle outline with a
          // bold `!` centered — the chip equivalent of the bad X-in-
          // circle and good tick-in-circle. Lucide's TriangleAlert ships
          // the `!` baked in but at a stroke weight that doesn't match
          // the bad/good chips; rolling our own keeps the family
          // visually consistent. The `icon` prop is ignored for warn.
          <div className="relative size-[18px] shrink-0">
            <svg
              viewBox="0 0 18 18"
              className={cn("absolute inset-0 size-full", TONE_TITLE[tone])}
              fill="currentColor"
              fillOpacity={0.15}
              stroke="currentColor"
              strokeWidth={1.5}
              strokeLinejoin="round"
              aria-hidden
            >
              {/* Explicit arc at each vertex (radius ~1.6) so corners
                  read as rounded regardless of stroke width — relying
                  on `strokeLinejoin="round"` alone gives a too-subtle
                  curve at this stroke weight. Tinted fill mirrors the
                  bad/good circular chips' `bg-<tone>/20`. */}
              <path d="M7.62 2.81 L1.71 13.04 A1.6 1.6 0 0 0 3.09 15.5 L14.91 15.5 A1.6 1.6 0 0 0 16.29 13.04 L10.38 2.81 A1.6 1.6 0 0 0 7.62 2.81 Z" />
            </svg>
            <span
              className={cn(
                "absolute inset-0 grid place-items-center pt-[2px] text-[10px] font-bold leading-none",
                TONE_TITLE[tone],
              )}
            >
              !
            </span>
          </div>
        ) : (
          <div
            className={cn(
              "grid size-[18px] shrink-0 place-items-center rounded-full border text-[11px] font-semibold",
              TONE_CHIP[tone],
            )}
          >
            {icon}
          </div>
        )}
        <Text
          as="span"
          variant="body"
          className={cn("min-w-0 flex-1 text-[12px] font-semibold", TONE_TITLE[tone])}
        >
          {title}
        </Text>
        <Chevron className="text-fg-4 size-3 shrink-0" />
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
