/*
 * Collapsible card for a single PMD code-smell violation. Sibling of
 * `FileDiffCard`: shares the outer chrome (rounded panel, `bg-bg-3` body,
 * `bg-bg-2` header with chevron + file path) so smell cards and diff
 * cards sit cleanly side-by-side in the detail panel.
 *
 * The body delegates to `@pierre/diffs`'s `<FileDiff>` for syntax
 * highlighting + line numbers. The analysis pipeline emits the snippet
 * already in unified-diff form (a single hunk of context lines with the
 * absolute line range in the `@@` header), so we feed it straight
 * through `parsePatchFiles` — no diff is actually being shown, the
 * format is just the cheapest way to get Shiki rendering with proper
 * absolute line numbers.
 *
 * The violation range is highlighted via the library's `selectedLines`
 * with its default selection styling.
 */

import { ChevronDownIcon, ChevronRightIcon, FileIcon } from "lucide-react"
import { useState } from "react"
import { parsePatchFiles } from "@pierre/diffs"
import type { FileDiffMetadata } from "@pierre/diffs"
import { FileDiff } from "@pierre/diffs/react"
import { Text } from "@/components/text"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { DIFF_SHADOW_CSS } from "@/lib/diff-shadow-css"
import { cn } from "@/lib/utils"

type ThemeOption = string | { dark: string; light: string }

/** Trajectory state of the violation relative to the selected checkpoint:
 *  `new` = first seen here, `carried` = inherited from earlier (default),
 *  `resolved` = was at the previous checkpoint and no longer fires. Drives
 *  a small status pill in the header and (for `resolved`) muted opacity. */
export type CodeSmellState = "new" | "carried" | "resolved"

export type CodeSmellCardProps = {
  rule: string
  /** PMD severity 1..5; 1 = highest. Drives the priority pill colour. */
  priority: number
  /** Relative file path. */
  file: string
  /** 1-based inclusive violation line range. `endLine === beginLine` for point violations. */
  beginLine: number
  endLine: number
  /**
   * Self-contained mini unified-diff text wrapping the snippet (one hunk,
   * all context lines, absolute line numbers in the `@@` header). Null /
   * empty when the analysis pipeline couldn't read the source — the body
   * falls back to a muted "source unavailable" line.
   */
  patch?: string | null
  /** PMD's free-text rule description. Optional header subtitle. */
  message?: string
  /** Rule-set badge text (e.g. `design`, `errorprone`). */
  ruleSet?: string
  /**
   * Stable cache key forwarded to `parsePatchFiles`'s worker pool. Without
   * one, the parser re-walks the patch on every render. Pass something
   * like `"smell-${sha}-${index}"` from the call site.
   */
  cacheKey?: string
  /** Trajectory state — see [CodeSmellState]. Defaults to `carried`. */
  state?: CodeSmellState
  defaultOpen?: boolean
  className?: string
  /** Pin the card to a fixed width; otherwise it fills its parent. */
  width?: number | string
  theme?: ThemeOption
}

const DEFAULT_THEME: ThemeOption = "Islands Dark"

const PRIORITY_TONES: Record<number, string> = {
  1: "text-bad",
  2: "text-warn",
  3: "text-fg-3",
  4: "text-fg-4",
  5: "text-fg-4",
}

// Header pill for trajectory state. `carried` is the default state and
// shows nothing — leaving the header uncluttered for the common case.
const STATE_BADGES: Record<CodeSmellState, { label: string; tone: string } | null> = {
  new: { label: "NEW", tone: "text-bad" },
  carried: null,
  resolved: { label: "RESOLVED", tone: "text-good" },
}

export function CodeSmellCard({
  rule,
  priority,
  file,
  beginLine,
  endLine,
  patch,
  message,
  ruleSet,
  cacheKey,
  state = "carried",
  defaultOpen = true,
  className,
  width,
  theme = DEFAULT_THEME,
}: CodeSmellCardProps) {
  const [open, setOpen] = useState(defaultOpen)

  const Chevron = open ? ChevronDownIcon : ChevronRightIcon
  const headerLabel = basename(file)
  const lineLabel = beginLine === endLine ? `L${beginLine}` : `L${beginLine}–${endLine}`
  const priorityTone = PRIORITY_TONES[priority] ?? "text-fg-4"
  const stateBadge = STATE_BADGES[state]

  // Gate parsing behind `open`: a checkpoint can flag 100+ violations
  // and the section renders them all flat. Eagerly parsing each one
  // would mean 100+ worker dispatches on mount even though most cards
  // stay collapsed.
  const fileDiff: FileDiffMetadata | undefined = open && patch
    ? parsePatchFiles(patch, cacheKey ?? `smell-${file}-${beginLine}`)[0]?.files[0]
    : undefined

  return (
    <div
      className={cn(
        "bg-bg-3 border-border-strong w-full min-w-0 overflow-hidden rounded-md border",
        // Resolved violations no longer exist at this checkpoint; mute
        // the whole card so it reads as "historical" without hiding the
        // content the user came to see.
        state === "resolved" && "opacity-70",
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
          <TooltipContent className="max-w-[520px] break-all">{file}</TooltipContent>
        </Tooltip>
        {/* Rule name styled like FileDiffCard's MODIFIED label — muted,
            mono, tight tracking. Capped at half-width so a long PMD
            rule name (e.g. `AvoidProtectedFieldInFinalClass`) can't
            squeeze the filename out, but a short one yields the spare
            width back to the filename instead of leaving a gap. */}
        <Tooltip>
          <TooltipTrigger asChild>
            <span className="text-fg-3 min-w-0 max-w-[50%] cursor-default truncate font-mono text-[10px] tracking-[0.04em]">
              {rule}
            </span>
          </TooltipTrigger>
          <TooltipContent className="max-w-[520px] break-all">{rule}</TooltipContent>
        </Tooltip>
        {stateBadge ? (
          <span
            className={cn(
              "shrink-0 font-mono text-[10px] tracking-[0.06em] uppercase",
              stateBadge.tone,
            )}
          >
            {stateBadge.label}
          </span>
        ) : null}
      </button>

      {open && (
        <div className="flex flex-col">
          {message || ruleSet ? (
            <div className="border-border bg-bg-2/50 flex flex-col gap-1 border-b px-3 py-2">
              {/* Priority + line range live here now (out of the header
                  per design intent) so the body keeps all metadata
                  the reader might still want, just one click away. */}
              <Text
                as="span"
                variant="monoCaption"
                tone="fg-4"
                className="uppercase tracking-[0.08em]"
              >
                <span className={priorityTone}>P{priority}</span>
                <span className="px-1">·</span>
                <span>{lineLabel}</span>
                {ruleSet ? (
                  <>
                    <span className="px-1">·</span>
                    <span>{ruleSet}</span>
                  </>
                ) : null}
              </Text>
              {message ? (
                <Text as="p" variant="bodySm" tone="fg-2">
                  {message}
                </Text>
              ) : null}
            </div>
          ) : null}

          {fileDiff ? (
            <div className="w-full overflow-x-auto">
              <FileDiff
                fileDiff={fileDiff}
                selectedLines={{
                  start: beginLine,
                  end: endLine,
                  side: "additions",
                  endSide: "additions",
                }}
                options={{
                  theme,
                  diffStyle: "unified",
                  // "simple" suppresses the leading "N unmodified lines"
                  // expansion control the renderer would otherwise inject
                  // for the gap between line 1 and our hunk's first line.
                  hunkSeparators: "simple",
                  disableFileHeader: true,
                  enableLineSelection: true,
                  unsafeCSS: DIFF_SHADOW_CSS,
                }}
              />
            </div>
          ) : (
            <Text
              as="p"
              variant="bodySm"
              tone="fg-4"
              className="px-3 py-3 italic"
            >
              source unavailable
            </Text>
          )}
        </div>
      )}
    </div>
  )
}

function basename(path: string): string {
  const slash = path.lastIndexOf("/")
  return slash === -1 ? path : path.substring(slash + 1)
}
