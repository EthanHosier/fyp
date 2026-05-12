/*
 * Collapsible card for a single CPD clone group. Sibling of
 * `CodeSmellCard` — shares the outer chrome (rounded panel, `bg-bg-3`
 * body, `bg-bg-2` header with chevron) so duplication and smell cards
 * sit cleanly together in the detail panel.
 *
 * Each clone group has N occurrences (≥2). The header summarises the
 * group; the expanded body lists every occurrence with its
 * `<FileDiff>` snippet rendered via `@pierre/diffs`.
 */

import { ChevronDownIcon, ChevronRightIcon, FileIcon } from "lucide-react"
import { useState } from "react"
import { parsePatchFiles } from "@pierre/diffs"
import type { FileDiffMetadata } from "@pierre/diffs"
import { FileDiff } from "@pierre/diffs/react"
import { Text } from "@/components/text"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import type { DuplicationGroupVM, DuplicationOccurrenceVM } from "@/data/types"
import { DIFF_SHADOW_CSS } from "@/lib/diff-shadow-css"
import { cn } from "@/lib/utils"

type ThemeOption = string | { dark: string; light: string }

/** Trajectory state of the clone group at the selected checkpoint. */
export type DuplicationState = "new" | "resolved"

export type DuplicationCardProps = {
  group: DuplicationGroupVM
  /** Stable cache key forwarded to `parsePatchFiles`'s worker pool. */
  cacheKey?: string
  defaultOpen?: boolean
  className?: string
  theme?: ThemeOption
}

const DEFAULT_THEME: ThemeOption = "Islands Dark"

const STATE_BADGES: Record<DuplicationState, { label: string; tone: string }> = {
  new: { label: "NEW", tone: "text-bad" },
  resolved: { label: "RESOLVED", tone: "text-good" },
}

export function DuplicationCard({
  group,
  cacheKey,
  defaultOpen = false,
  className,
  theme = DEFAULT_THEME,
}: DuplicationCardProps) {
  const [open, setOpen] = useState(defaultOpen)
  const Chevron = open ? ChevronDownIcon : ChevronRightIcon
  const stateBadge = STATE_BADGES[group.state]
  const first = group.occurrences[0]
  const headerLabel = first ? basename(first.file) : "(no occurrences)"
  const summary = `${group.occurrences.length} occurrences · ${group.lines}L · ${group.tokens}t`

  return (
    <div
      className={cn(
        "bg-bg-3 border-border-strong w-full min-w-0 overflow-hidden rounded-md border",
        group.state === "resolved" && "opacity-70",
        className,
      )}
    >
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="bg-bg-2 border-border-strong flex w-full items-center gap-2 border-b px-2.5 py-1.5 text-left data-[open=false]:border-b-0"
        data-open={open}
      >
        <Chevron className="text-fg-4 size-3 shrink-0" />
        <FileIcon className="text-fg-4 size-3 shrink-0" />
        <Tooltip>
          <TooltipTrigger asChild>
            <Text
              as="span"
              variant="mono"
              tone="fg"
              className="min-w-0 flex-1 truncate text-[11px]"
            >
              {headerLabel}
            </Text>
          </TooltipTrigger>
          <TooltipContent className="max-w-[520px] break-all">
            {first ? `${first.file}:L${first.beginLine}-${first.endLine}` : "—"}
          </TooltipContent>
        </Tooltip>
        <span className="text-fg-3 shrink-0 font-mono text-[10px] tracking-[0.04em]">
          {summary}
        </span>
        <span
          className={cn(
            "shrink-0 font-mono text-[10px] tracking-[0.06em] uppercase",
            stateBadge.tone,
          )}
        >
          {stateBadge.label}
        </span>
      </button>

      {open && (
        <div className="flex flex-col">
          {group.occurrences.map((occ, i) => (
            <OccurrenceBody
              key={`${occ.file}:${occ.beginLine}:${i}`}
              occurrence={occ}
              cacheKey={`${cacheKey ?? `dup-${group.identity}`}-${i}`}
              theme={theme}
              isLast={i === group.occurrences.length - 1}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function OccurrenceBody({
  occurrence,
  cacheKey,
  theme,
  isLast,
}: {
  occurrence: DuplicationOccurrenceVM
  cacheKey: string
  theme: ThemeOption
  isLast: boolean
}) {
  const fileDiff: FileDiffMetadata | undefined = occurrence.snippetPatch
    ? parsePatchFiles(occurrence.snippetPatch, cacheKey)[0]?.files[0]
    : undefined
  const lineLabel =
    occurrence.beginLine === occurrence.endLine
      ? `L${occurrence.beginLine}`
      : `L${occurrence.beginLine}–${occurrence.endLine}`

  return (
    <div className={cn("flex flex-col", !isLast && "border-border border-b")}>
      <div className="bg-bg-2/50 border-border flex items-center gap-2 border-b px-3 py-1.5">
        <Text
          as="span"
          variant="monoCaption"
          tone="fg-3"
          className="min-w-0 flex-1 truncate"
        >
          {occurrence.file}
        </Text>
        <Text
          as="span"
          variant="monoCaption"
          tone="fg-4"
          className="shrink-0 uppercase tracking-[0.08em]"
        >
          {lineLabel}
        </Text>
      </div>
      {fileDiff ? (
        <div className="w-full overflow-x-auto">
          <FileDiff
            fileDiff={fileDiff}
            selectedLines={{
              start: occurrence.beginLine,
              end: occurrence.endLine,
              side: "additions",
              endSide: "additions",
            }}
            options={{
              theme,
              diffStyle: "unified",
              hunkSeparators: "simple",
              disableFileHeader: true,
              enableLineSelection: true,
              unsafeCSS: DIFF_SHADOW_CSS,
            }}
          />
        </div>
      ) : (
        <Text as="p" variant="bodySm" tone="fg-4" className="px-3 py-3 italic">
          source unavailable
        </Text>
      )}
    </div>
  )
}

function basename(path: string): string {
  const slash = path.lastIndexOf("/")
  return slash === -1 ? path : path.substring(slash + 1)
}
