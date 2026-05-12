/*
 * Collapsible per-file diff card. Wraps `@pierre/diffs`'s React `FileDiff`
 * renderer with our own IntelliJ-ish header (path + kind badge + +/- counts),
 * built from the `FileDiffMetadata` the library already parsed. The library's
 * own file header is suppressed so the only chrome around the code body is
 * ours.
 *
 * Given a raw unified-diff string, use `parsePatchString` from
 * `@/lib/patch` (or call `parsePatchFiles` directly) and render one card
 * per file.
 */

import { ChevronDownIcon, ChevronRightIcon, FileIcon } from "lucide-react"
import { useState } from "react"
import { FileDiff } from "@pierre/diffs/react"
import type { FileDiffMetadata } from "@pierre/diffs"
import { Text } from "@/components/text"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { DIFF_SHADOW_CSS } from "@/lib/diff-shadow-css"
import { cn } from "@/lib/utils"

type Kind = "new" | "deleted" | "renamed" | "modified"

type ThemeOption = string | { dark: string; light: string }

type FileDiffCardProps = {
  fileDiff: FileDiffMetadata
  defaultOpen?: boolean
  className?: string
  // Pin the card to a fixed width. Without it the card fills its parent
  // (`w-full`), which — inside auto-sized flex ancestors — lets wide diff
  // lines stretch the card on expand. Setting `width` caps it absolutely
  // and moves the overflow into the inner scroll area.
  width?: number | string
  // Shiki theme name (or {dark,light} pair) for the code body. Pass a
  // registered custom theme's `name` field to use Darcula, etc.
  theme?: ThemeOption
}

const DEFAULT_THEME: ThemeOption = "Islands Dark"

const KIND_LABELS: Record<Kind, string> = {
  new: "NEW",
  deleted: "DELETED",
  renamed: "RENAMED",
  modified: "MODIFIED",
}

const KIND_TONES: Record<Kind, string> = {
  new: "text-good",
  deleted: "text-bad",
  renamed: "text-warn",
  modified: "text-fg-3",
}

export function FileDiffCard({
  fileDiff,
  defaultOpen = true,
  className,
  width,
  theme = DEFAULT_THEME,
}: FileDiffCardProps) {
  const [open, setOpen] = useState(defaultOpen)

  const kind = kindOf(fileDiff)
  const { added, deleted } = countChanges(fileDiff)
  const path = fileDiff.name
  const prevPath = fileDiff.prevName
  const renamed = prevPath && prevPath !== path
  // Bare filename for the header title; full path(s) live in the hover
  // tooltip so the card reads cleanly at narrow panel widths but the
  // full context is still one hover away.
  const headerLabel = renamed
    ? `${basename(prevPath)} → ${basename(path)}`
    : basename(path)
  const fullLabel = renamed ? `${prevPath} → ${path}` : path
  const Chevron = open ? ChevronDownIcon : ChevronRightIcon

  return (
    <div
      className={cn(
        "bg-bg-3 border-border-strong w-full min-w-0 overflow-hidden rounded-md border",
        className,
      )}
      style={width !== undefined ? { width } : undefined}
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
            {fullLabel}
          </TooltipContent>
        </Tooltip>
        <span
          className={cn(
            "font-mono text-[10px] tracking-[0.06em] uppercase",
            KIND_TONES[kind],
          )}
        >
          {KIND_LABELS[kind]}
        </span>
        <Text variant="mono" tone="good" className="text-[11px]">
          +{added}
        </Text>
        <Text variant="mono" tone="bad" className="text-[11px]">
          −{deleted}
        </Text>
      </button>

      {open && (
        // No vertical scroll here — let the body grow to full content
        // height so the panel's outer ScrollArea is the only scrollbar.
        // Horizontal overflow still scrolls inside for long diff lines.
        <div className="w-full overflow-x-auto">
          <FileDiff
            fileDiff={fileDiff}
            options={{
              theme,
              diffStyle: "unified",
              hunkSeparators: "line-info-basic",
              disableFileHeader: true,
              unsafeCSS: DIFF_SHADOW_CSS,
            }}
          />
        </div>
      )}
    </div>
  )
}

function basename(path: string): string {
  const slash = path.lastIndexOf("/")
  return slash === -1 ? path : path.substring(slash + 1)
}

function kindOf(file: FileDiffMetadata): Kind {
  switch (file.type) {
    case "new":
      return "new"
    case "deleted":
      return "deleted"
    case "rename-pure":
    case "rename-changed":
      return "renamed"
    default:
      return "modified"
  }
}

function countChanges(file: FileDiffMetadata): { added: number; deleted: number } {
  let added = 0
  let deleted = 0
  for (const h of file.hunks) {
    added += h.additionLines
    deleted += h.deletionLines
  }
  return { added, deleted }
}
