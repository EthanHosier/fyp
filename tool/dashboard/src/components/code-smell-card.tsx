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
import { cn } from "@/lib/utils"

type ThemeOption = string | { dark: string; light: string }

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

  // parsePatchFiles is sync — it just dispatches highlight work to the
  // worker pool. A missing/empty patch falls through to the unavailable
  // state without us needing a second null check below.
  const fileDiff: FileDiffMetadata | undefined = patch
    ? parsePatchFiles(patch, cacheKey ?? `smell-${file}-${beginLine}`)[0]?.files[0]
    : undefined

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
          <TooltipContent className="max-w-[520px] break-all">{file}</TooltipContent>
        </Tooltip>
        <Text variant="mono" tone="fg-3" className="shrink-0 text-[10.5px]">
          {lineLabel}
        </Text>
        <Text variant="mono" tone="fg-2" className="shrink-0 truncate text-[11px]">
          {rule}
        </Text>
        <span
          className={cn(
            "font-mono text-[10px] tracking-[0.06em] uppercase",
            priorityTone,
          )}
        >
          P{priority}
        </span>
      </button>

      {open && (
        <div className="flex flex-col">
          {message ? (
            <div className="border-border bg-bg-2/50 border-b px-3 py-2">
              <Text as="p" variant="bodySm" tone="fg-2">
                {message}
              </Text>
              {ruleSet ? (
                <Text
                  as="span"
                  variant="monoCaption"
                  tone="fg-4"
                  className="mt-1 inline-block uppercase tracking-[0.08em]"
                >
                  {ruleSet}
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
