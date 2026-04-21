import { parsePatchFiles } from "@pierre/diffs"
import { FileDiffCard } from "@/components/file-diff-card"
import { Text } from "@/components/text"

/**
 * Detail-panel section that renders a unified-diff patch string as a
 * stack of `FileDiffCard`s. Shared between the checkpoint body (raw
 * transition diff) and the refactoring body (hunk-filtered diff).
 *
 * `cacheKey` is forwarded to `parsePatchFiles` so @pierre/diffs' Worker
 * Pool AST cache can reuse parses across re-renders when the same
 * selection is reopened — otherwise we re-parse every mount.
 */
export function DiffSection({
  title,
  patch,
  cacheKey,
  emptyMessage = "No textual change.",
}: {
  title: string
  patch: string
  cacheKey: string
  emptyMessage?: string
}) {
  const files = patch ? (parsePatchFiles(patch, cacheKey)[0]?.files ?? []) : []

  return (
    <section className="flex flex-col gap-2">
      <Text as="h3" variant="eyebrow" tone="fg-4">
        {title}
      </Text>
      {files.length === 0 ? (
        <Text variant="body" tone="fg-3" className="text-[12px]">
          {emptyMessage}
        </Text>
      ) : (
        // `grid` with a `minmax(0, 1fr)` column pins each card to the
        // available width, so wide diff lines scroll inside the card
        // instead of stretching the whole panel.
        <div className="grid grid-cols-[minmax(0,1fr)] gap-2">
          {files.map((file, i) => (
            <FileDiffCard
              key={file.name + i}
              fileDiff={file}
              defaultOpen={i === 0}
            />
          ))}
        </div>
      )}
    </section>
  )
}
